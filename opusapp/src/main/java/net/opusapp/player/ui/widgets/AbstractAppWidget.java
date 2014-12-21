/*
 * AppWidgetBase.java
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
package net.opusapp.player.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.utils.LogUtils;


public abstract class AbstractAppWidget extends AppWidgetProvider {

    public static final String TAG = AbstractAppWidget.class.getSimpleName();



    protected static boolean isPlaying;

    protected static String track;

    protected static String artist;

    protected static String album;

    protected static Bitmap albumImage;

    protected static boolean hasPlaylist = false;



    protected final PendingIntent APPWIDGET_PAUSE_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_APPWIDGET, PlayerService.ACTION_TOGGLEPAUSE);

    protected final PendingIntent APPWIDGET_NEXT_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_APPWIDGET, PlayerService.ACTION_NEXT);

    protected final PendingIntent APPWIDGET_PREV_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_APPWIDGET, PlayerService.ACTION_PREVIOUS);



    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);


    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            PlayerService.APPWIDGET_REFRESH_INTENT.send();
        }
        catch (final PendingIntent.CanceledException canceledException) {
            LogUtils.LOGException(TAG, "pushUpdate", 0, canceledException);
        }
    }



    public static void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public static void setHasPlaylist(boolean hasPlaylist) {
        AbstractAppWidget.hasPlaylist = hasPlaylist;
    }

    public static void setMetadata(final String trackName, final String artistName, final String albumName, final Bitmap art) {
        track = trackName;
        artist = artistName;
        album = albumName;

        albumImage = art;
    }

    protected abstract void applyUpdate(Context context);

    protected void notifyUpdate(final Context context, final int[] appWidgetIds, final RemoteViews views) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views);
        } else {
            appWidgetManager.updateAppWidget(new ComponentName(context, getClass()), views);
        }
    }
}
