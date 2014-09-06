/*
 * Widget4x1.java
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
package eu.chepy.opus.player.ui.widgets;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.PlayerService;
import eu.chepy.opus.player.ui.activities.LibraryMainActivity;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class Widget4x1 extends AppWidgetBase {

	private static Widget4x1 instance;

    private boolean initialized = false;
	
	public static synchronized Widget4x1 getInstance() {
		if (instance == null) {
			instance = new Widget4x1();
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
        final RemoteViews appWidgetViews = new RemoteViews(context.getPackageName(), R.layout.home_widget_4x1);
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

    public void notifyChange(final PlayerService service, final String trackName, final String artistName, final String albumName, final Bitmap art, boolean isPlaying) {
        if (hasInstances(service)) {
            initialized = true;
            performUpdate(service, null, trackName, artistName, albumName, art, isPlaying);
        }
    }

    public void uninit(final PlayerService service) {
        initialized = false;
        performUpdate(service, null, null, null, null, null, false);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void performUpdate(final PlayerService service, final int[] appWidgetIds, final String trackName, final String artistName, final String albumName, final Bitmap art, boolean isPlaying) {
        if (initialized) {
            final RemoteViews appWidgetView = new RemoteViews(service.getPackageName(), R.layout.home_widget_4x1);

            // Set the titles and artwork
            appWidgetView.setTextViewText(R.id.four_by_one_trackname, trackName);
            appWidgetView.setTextViewText(R.id.four_by_one_separator, " - ");
            appWidgetView.setTextViewText(R.id.four_by_one_artistname, artistName);

            if (art != null) {
                appWidgetView.setImageViewBitmap(R.id.four_by_one_albumart, art);
            }
            else {
                appWidgetView.setImageViewResource(R.id.four_by_one_albumart, R.drawable.no_art_small);
            }

            // Set correct drawable for pause state
            if (isPlaying) {
                appWidgetView.setImageViewResource(R.id.four_by_one_control_play, R.drawable.btn_playback_pause_transparent);
                if (PlayerApplication.hasICS_MR1()) {
                    appWidgetView.setContentDescription(R.id.four_by_one_control_play, service.getString(R.string.imageview_content_description_play));
                }
            } else {
                appWidgetView.setImageViewResource(R.id.four_by_one_control_play, R.drawable.btn_playback_play_transparent);
                if (PlayerApplication.hasICS_MR1()) {
                    appWidgetView.setContentDescription(R.id.four_by_one_control_play, service.getString(R.string.imageview_content_description_play));
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
        views.setOnClickPendingIntent(R.id.four_by_one_info, pendingIntent);
        views.setOnClickPendingIntent(R.id.four_by_one_albumart, pendingIntent);

        // Previous track
        views.setOnClickPendingIntent(R.id.four_by_one_control_prev, buildPendingIntent(context, 3, serviceName));

        // Play and pause
        views.setOnClickPendingIntent(R.id.four_by_one_control_play, buildPendingIntent(context, 1, serviceName));

        // Next track
        views.setOnClickPendingIntent(R.id.four_by_one_control_next, buildPendingIntent(context, 2, serviceName));
    }

    private void linkUnavailable(final Context context, final RemoteViews views) {
        Intent action;
        PendingIntent pendingIntent;

        action = new Intent(context, LibraryMainActivity.class);
        pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
        views.setOnClickPendingIntent(R.id.unavailable, pendingIntent);
    }
}
