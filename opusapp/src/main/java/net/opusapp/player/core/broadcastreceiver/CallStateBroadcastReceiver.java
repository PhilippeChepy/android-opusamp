/*
 * CallStateBroadcastReceiver.java
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
package net.opusapp.player.core.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.ui.utils.PlayerApplication;

public class CallStateBroadcastReceiver extends BroadcastReceiver {

	public static final String TAG = CallStateBroadcastReceiver.class.getSimpleName();



	@Override
	public void onReceive(Context context, Intent intent) {
		PhoneStateListener phoneListener = new PhoneStateListener() {
			
			public void onCallStateChanged(int state, String incomingNumber) {
                final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
				switch (state) {
				case TelephonyManager.CALL_STATE_IDLE:
                        localBroadcastManager.sendBroadcast(PlayerService.TELEPHONY_PLAY_INTENT);
					break;
				case TelephonyManager.CALL_STATE_OFFHOOK:
				case TelephonyManager.CALL_STATE_RINGING:
                    localBroadcastManager.sendBroadcast(PlayerService.TELEPHONY_PAUSE_INTENT);
					break;
				}
			}
		};

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getBoolean(PlayerApplication.context.getString(R.string.preference_key_pause_call), true)) {
            TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
	}

}
