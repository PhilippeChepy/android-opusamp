/*
 * NotificationHelper.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package net.opusapp.player.core;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.utils.PlayerApplication;

@SuppressLint("NewApi")
public class NotificationHelper {

    private RemoteViews notificationTemplate;

    private RemoteViews expandedView;

    private Notification notification = null;

    private final NotificationManager notificationManager;



    private final PlayerService service;



    public NotificationHelper(final PlayerService service) {
        this.service = service;
        notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void buildNotification(final String albumName, final String artistName, final String trackName, final Bitmap albumArt) {
    	notificationTemplate = new RemoteViews(service.getPackageName(), R.layout.notification_template_base);
        
        initCollapsedLayout(trackName, artistName, albumArt);

        if (PlayerApplication.hasHoneycomb()) {
            notification = new NotificationCompat.Builder(service)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(getPendingIntent())
                    .setContent(notificationTemplate)
                    .build();

            initPlaybackActions();
            if (PlayerApplication.hasJellyBean()) {
                expandedView = new RemoteViews(service.getPackageName(), R.layout.notification_template_expanded_base);
                
                notification.bigContentView = expandedView;

                initExpandedPlaybackActions();
                initExpandedLayout(trackName, albumName, artistName, albumArt);
            }
        } else {
            notification = new Notification();
            notification.contentView = notificationTemplate;
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.icon = R.drawable.ic_notification;
            notification.contentIntent = getPendingIntent();
        }
    }
    
    public Notification getNotification() {
    	return notification;
    }

    private PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(service, 0, new Intent(service.getApplicationContext(), LibraryMainActivity.class), 0);
    }

    public void goToIdleState(final boolean isPlaying) {
        if (notification == null || notificationManager == null) {
            return;
        }
        
        if (notificationTemplate != null) {
            notificationTemplate.setImageViewResource(R.id.notification_base_play,
                    isPlaying ? R.drawable.btn_playback_pause_transparent : R.drawable.btn_playback_play_transparent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && expandedView != null) {
            expandedView.setImageViewResource(R.id.notification_expanded_base_play,
                    isPlaying ? R.drawable.btn_playback_pause_transparent : R.drawable.btn_playback_play_transparent);
        }
        
        notificationManager.notify(PlayerApplication.NOTIFICATION_PLAY_ID, notification);
    }

    private void initCollapsedLayout(final String trackName, final String artistName, final Bitmap albumArt) {
        notificationTemplate.setTextViewText(R.id.notification_base_line_one, trackName);
        notificationTemplate.setTextViewText(R.id.notification_base_line_two, artistName);
        
        if (albumArt != null) {
        	notificationTemplate.setImageViewBitmap(R.id.notification_base_image, albumArt);
        }
    }

    private void initExpandedLayout(final String trackName, final String artistName, final String albumName, final Bitmap albumArt) {
        expandedView.setTextViewText(R.id.notification_expanded_base_line_one, trackName);
        expandedView.setTextViewText(R.id.notification_expanded_base_line_two, albumName);
        expandedView.setTextViewText(R.id.notification_expanded_base_line_three, artistName);
        
        if (albumArt != null) {
            expandedView.setImageViewBitmap(R.id.notification_expanded_base_image, albumArt);
        }
    }

    private void initExpandedPlaybackActions() {
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play, retrievePlaybackActions(1));
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next, retrievePlaybackActions(2));
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous, retrievePlaybackActions(3));
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse, retrievePlaybackActions(4));
        expandedView.setImageViewResource(R.id.notification_expanded_base_play, R.drawable.btn_playback_pause_transparent);
    }

    private void initPlaybackActions() {
        // Play and playerPause
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_play, retrievePlaybackActions(1));

        // Skip tracks
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_next, retrievePlaybackActions(2));

        // Previous tracks
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_previous, retrievePlaybackActions(3));

        // Stop and collapse the notification
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_collapse, retrievePlaybackActions(4));

        // Update the playerPlay button image
        notificationTemplate.setImageViewResource(R.id.notification_base_play, R.drawable.btn_playback_pause_transparent);
    }

    private PendingIntent retrievePlaybackActions(final int which) {
    	Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
    	PendingIntent pendingIntent;
        
        switch (which) {
            case 1:
                // Play and playerPause
                action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_TOGGLEPAUSE);
                pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 1, action, 0);
                return pendingIntent;
            case 2:
                // Next track
            	action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_NEXT);
                pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 2, action, 0);
                return pendingIntent;
            case 3:
                // Previous track
            	action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PREVIOUS);
                pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 3, action, 0);
                return pendingIntent;
            case 4:
                // Stop and collapse the notification
            	action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_STOP);
                pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 4, action, 0);
                return pendingIntent;
            default:
                break;
        }
        return null;
    }

}
