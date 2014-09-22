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
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import net.opusapp.player.core.service.PlayerService;

public abstract class AppWidgetBase extends AppWidgetProvider {

    protected PendingIntent buildPendingIntent(Context context, int which, final ComponentName serviceName) {
        Intent action = new Intent(PlayerService.ACTION_APPWIDGET_COMMAND);
        action.setComponent(serviceName);

        PendingIntent pendingIntent;

        switch (which) {
            case 1:
                // Play and playerPause
                action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_TOGGLEPAUSE);
                pendingIntent = PendingIntent.getService(context, 1, action, 0);
                return pendingIntent;
            case 2:
                // Next track
                action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_NEXT);
                pendingIntent = PendingIntent.getService(context, 2, action, 0);
                return pendingIntent;
            case 3:
                // Previous track
                action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PREVIOUS);
                pendingIntent = PendingIntent.getService(context, 3, action, 0);
                return pendingIntent;
            case 4:
                // Stop and collapse the notification
                action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_STOP);
                pendingIntent = PendingIntent.getService(context, 4, action, 0);
                return pendingIntent;
            default:
                break;
        }

        return null;
    }
}
