/*
 * MediaButtonBroadcastReceiver.java
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
package eu.chepy.audiokit.core.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import eu.chepy.audiokit.ui.utils.MusicConnector;

public class MediaButtonBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = MediaButtonBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
            final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getAction() == KeyEvent.ACTION_UP) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    MusicConnector.doPlayAction(true);
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    MusicConnector.doNextAction();
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    MusicConnector.doPrevAction();
                }
            }
        }
    }
}
