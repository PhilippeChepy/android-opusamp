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

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;


public abstract class AbstractAppWidget extends AppWidgetProvider {

    protected static boolean isPlaying;

    protected static String track;

    protected static String artist;

    protected static String album;

    protected static Bitmap albumImage;

    protected static boolean hasPlaylist = false;



    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        pushUpdate(context, appWidgetIds);
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

    public void applyUpdate(Context context) {
        doUpdate(context);
    }

    protected abstract void pushUpdate(Context context, final int[] appWidgetIds);

    protected abstract void doUpdate(Context context);

    protected void notifyUpdate(final Context context, final int[] appWidgetIds, final RemoteViews views) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views);
        } else {
            appWidgetManager.updateAppWidget(new ComponentName(context, getClass()), views);
        }
    }
}
