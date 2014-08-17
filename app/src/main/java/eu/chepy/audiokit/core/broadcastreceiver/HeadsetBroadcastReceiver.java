/*
 * HeadsetBroadcastReceiver.java
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
package eu.chepy.audiokit.core.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class HeadsetBroadcastReceiver extends BroadcastReceiver {

    private static final String EXTRA_STATE = "state";


    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(EXTRA_STATE)){
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (sharedPreferences.getBoolean(PlayerApplication.context.getString(R.string.preference_key_plug_auto_play), true)) {
                if (intent.getIntExtra(EXTRA_STATE, 0) == 1) {
                    if (!MusicConnector.isPlaying()) {
                        MusicConnector.doPlayAction();
                    }
                }
            }
        }
    }

}
