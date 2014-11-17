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


    private final PlayerService mService;


    private int mPlayDrawable;

    private int mPauseDrawable;



    public NotificationHelper(final PlayerService service) {
        mService = service;
        notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);

        if (PlayerApplication.hasLollipop()) {
            mPlayDrawable = R.drawable.ic_action_playback_play;
            mPauseDrawable = R.drawable.ic_action_playback_pause;
        }
        else {
            mPlayDrawable = R.drawable.ic_action_playback_play_dark;
            mPauseDrawable = R.drawable.ic_action_playback_pause_dark;
        }
    }

    public void buildNotification(final String albumName, final String artistName, final String trackName, final Bitmap albumArt) {
    	notificationTemplate = new RemoteViews(mService.getPackageName(), R.layout.notification_template_base);
        
        initCollapsedLayout(trackName, artistName, albumArt);

        if (PlayerApplication.hasHoneycomb()) {
            notification = new NotificationCompat.Builder(mService)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(getPendingIntent())
                    .setContent(notificationTemplate)
                    .build();

            initPlaybackActions();
            if (PlayerApplication.hasJellyBean()) {
                expandedView = new RemoteViews(mService.getPackageName(), R.layout.notification_template_expanded_base);
                
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
        return PendingIntent.getActivity(mService, 0, new Intent(mService.getApplicationContext(), LibraryMainActivity.class), 0);
    }

    public void goToIdleState(final boolean isPlaying) {
        if (notification == null || notificationManager == null) {
            return;
        }
        
        if (notificationTemplate != null) {
            notificationTemplate.setImageViewResource(R.id.notification_base_play,
                    isPlaying ? mPauseDrawable : mPlayDrawable);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && expandedView != null) {
            expandedView.setImageViewResource(R.id.notification_expanded_base_play,
                    isPlaying ? mPauseDrawable : mPlayDrawable);
        }
        
        notificationManager.notify(PlayerApplication.NOTIFICATION_PLAY_ID, notification);
    }

    public void forceUpdate() {
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
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
        expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

        expandedView.setImageViewResource(R.id.notification_expanded_base_play, mPauseDrawable);
    }

    private void initPlaybackActions() {
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
        notificationTemplate.setOnClickPendingIntent(R.id.notification_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

        notificationTemplate.setImageViewResource(R.id.notification_base_play, mPauseDrawable);
    }

}
