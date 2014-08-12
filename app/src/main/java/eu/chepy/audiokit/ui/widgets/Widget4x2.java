/*
 * Widget4x2.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.ui.widgets;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.ui.activities.LibraryMainActivity;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class Widget4x2 extends AppWidgetBase {

	private static Widget4x2 instance;

    private boolean initialized = false;
	
	public static synchronized Widget4x2 getInstance() {
		if (instance == null) {
			instance = new Widget4x2();
		}
		
		return instance;
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (initialized) {
            defaultAppWidget(context, appWidgetIds);
        }
        else {
            unavailableAppWidget(context, appWidgetIds);
        }
	}

    private void defaultAppWidget(final Context context, final int[] appWidgetIds) {
        final RemoteViews appWidgetViews = new RemoteViews(context.getPackageName(), R.layout.home_widget_4x2);
        linkButtons(context, appWidgetViews);
        pushUpdate(context, appWidgetIds, appWidgetViews);
    }

    private void unavailableAppWidget(final Context context, final int[] appWidgetIds) {
        final RemoteViews appWidgetViews = new RemoteViews(context.getPackageName(), R.layout.home_widget_start_app);
        linkUnavailable(context, appWidgetViews);
        pushUpdate(context, appWidgetIds, appWidgetViews);
    }

    private void pushUpdate(final Context context, final int[] appWidgetIds, final RemoteViews views) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views);
        } else {
            appWidgetManager.updateAppWidget(new ComponentName(context, getClass()), views);
        }
    }

    private boolean hasInstances(final Context context) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final int[] mAppWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, getClass()));
        return mAppWidgetIds.length > 0;
    }

    public void notifyChange(final PlayerService service, boolean hasData, final String trackName, final String artistName, final String albumName, final Bitmap art) {
        if (hasInstances(service)) {
            initialized = true;
            performUpdate(service, null, hasData, trackName, artistName, albumName, art);
        }
    }

    public void uninit(final PlayerService service) {
        initialized = false;
        performUpdate(service, null, false, null, null, null, null);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void performUpdate(final PlayerService service, final int[] appWidgetIds, boolean hasData, final String trackName, final String artistName, final String albumName, final Bitmap art) {
        if (initialized) {
            final RemoteViews appWidgetView = new RemoteViews(service.getPackageName(), R.layout.home_widget_4x2);

            if (hasData) {
                // Set the titles and artwork
                appWidgetView.setTextViewText(R.id.four_by_two_trackname, trackName);
                appWidgetView.setTextViewText(R.id.four_by_two_artistname, artistName);
                appWidgetView.setTextViewText(R.id.four_by_two_albumname, albumName);
                appWidgetView.setImageViewBitmap(R.id.four_by_two_albumart, art);
            }
            else {
                // Set the titles and artwork
                appWidgetView.setTextViewText(R.id.four_by_two_trackname, null);
                appWidgetView.setTextViewText(R.id.four_by_two_artistname, null);
                appWidgetView.setTextViewText(R.id.four_by_two_albumname, null);
                appWidgetView.setImageViewResource(R.id.four_by_two_albumart, R.drawable.no_art_small);
            }

            // Set correct drawable for pause state
            final boolean isPlaying = MusicConnector.isPlaying();
            if (isPlaying) {
                appWidgetView.setImageViewResource(R.id.four_by_two_control_play, R.drawable.btn_playback_pause_transparent);
                if (PlayerApplication.hasICS_MR1()) {
                    appWidgetView.setContentDescription(R.id.four_by_two_control_play, service.getString(R.string.cd_play));
                }
            } else {
                appWidgetView.setImageViewResource(R.id.four_by_two_control_play, R.drawable.btn_playback_play_transparent);
                if (PlayerApplication.hasICS_MR1()) {
                    appWidgetView.setContentDescription(R.id.four_by_two_control_play, service.getString(R.string.cd_play));
                }
            }

            // Link actions buttons to intents
            linkButtons(service, appWidgetView);

            // Update the app-widget
            pushUpdate(service, appWidgetIds, appWidgetView);
        }
        else {
            // Link actions buttons to intents
            final RemoteViews appWidgetView = new RemoteViews(service.getPackageName(), R.layout.home_widget_start_app);
            linkUnavailable(service, appWidgetView);

            // Update the app-widget
            pushUpdate(service, appWidgetIds, appWidgetView);
        }
    }

    private void linkButtons(final Context context, final RemoteViews views) {
        Intent action;
        PendingIntent pendingIntent;

        final ComponentName serviceName = new ComponentName(context, PlayerService.class);

        // Now playing
        action = new Intent(context, LibraryMainActivity.class);
        pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
        views.setOnClickPendingIntent(R.id.four_by_two_info, pendingIntent);
        views.setOnClickPendingIntent(R.id.four_by_two_albumart, pendingIntent);

        // Previous track
        views.setOnClickPendingIntent(R.id.four_by_two_control_prev, buildPendingIntent(context, 3, serviceName));

        // Play and pause
        views.setOnClickPendingIntent(R.id.four_by_two_control_play, buildPendingIntent(context, 1, serviceName));

        // Next track
        views.setOnClickPendingIntent(R.id.four_by_two_control_next, buildPendingIntent(context, 2, serviceName));
    }

    private void linkUnavailable(final Context context, final RemoteViews views) {
        Intent action;
        PendingIntent pendingIntent;

        action = new Intent(context, LibraryMainActivity.class);
        pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
        views.setOnClickPendingIntent(R.id.unavailable, pendingIntent);
    }
}
