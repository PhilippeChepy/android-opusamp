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
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.utils.PlayerApplication;

@SuppressLint("NewApi")
public class NotificationHelper {

    private RemoteViews mCollapsedView;

    private RemoteViews mExpandedView;

    private Notification notification = null;

    private final NotificationManager notificationManager;


    private final PlayerService mService;


    private int mPlayDrawable;

    private int mPauseDrawable;



    public NotificationHelper(final PlayerService service) {
        mService = service;
        notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);

        if (PlayerApplication.hasLollipop()) {
            mPlayDrawable = R.drawable.ic_play_arrow_grey600_48dp;
            mPauseDrawable = R.drawable.ic_pause_grey600_48dp;
        }
        else {
            mPlayDrawable = R.drawable.ic_play_arrow_white_48dp;
            mPauseDrawable = R.drawable.ic_pause_white_48dp;
        }
    }

    public void buildNotification(final String albumName, final String artistName, final String trackName, final Bitmap albumArt) {
        final Intent intent = new Intent(mService.getApplicationContext(), LibraryMainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0, intent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mService)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(albumArt)
                .setContentTitle(albumName)
                .setContentText(String.format(PlayerApplication.context.getString(R.string.notification_fallback_info_format), trackName, artistName))
                .setContentIntent(pendingIntent);

        if (PlayerApplication.hasHoneycomb()) {
            mCollapsedView = new RemoteViews(mService.getPackageName(), R.layout.notification_template_base);
            notification = builder
                    .setContent(mCollapsedView)
                    .build();

            // Actions
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

            mCollapsedView.setImageViewResource(R.id.notification_base_play, mPauseDrawable);

            // Media informations
            mCollapsedView.setTextViewText(R.id.notification_base_line_one, trackName);
            mCollapsedView.setTextViewText(R.id.notification_base_line_two, artistName);

            if (albumArt != null) {
                mCollapsedView.setImageViewBitmap(R.id.notification_base_image, albumArt);
            }
            else {
                mCollapsedView.setImageViewResource(R.id.notification_base_image, R.drawable.no_art_normal);
            }
        }
        else {
            notification = builder.build();
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.icon = R.drawable.ic_notification;
            notification.contentIntent = pendingIntent;
        }

        if (PlayerApplication.hasJellyBean()) {
            mExpandedView = new RemoteViews(mService.getPackageName(), R.layout.notification_template_expanded_base);
            notification.bigContentView = mExpandedView;

            // Actions
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

            mExpandedView.setImageViewResource(R.id.notification_expanded_base_play, mPauseDrawable);

            // Media informations
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_one, trackName);
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_two, albumName);
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_three, artistName);

            if (albumArt != null) {
                mExpandedView.setImageViewBitmap(R.id.notification_expanded_base_image, albumArt);
            }
            else {
                mExpandedView.setImageViewResource(R.id.notification_expanded_base_image, R.drawable.no_art_normal);
            }
        }
    }
    
    public Notification getNotification() {
    	return notification;
    }

    public void forceUpdate(final boolean isPlaying) {
        if (notification == null || notificationManager == null) {
            return;
        }
        
        if (mCollapsedView != null) {
            mCollapsedView.setImageViewResource(R.id.notification_base_play, isPlaying ? mPauseDrawable : mPlayDrawable);
        }

        if (mExpandedView != null) {
            mExpandedView.setImageViewResource(R.id.notification_expanded_base_play, isPlaying ? mPauseDrawable : mPlayDrawable);
        }
        
        notificationManager.notify(PlayerApplication.NOTIFICATION_PLAY_ID, notification);
    }
}
