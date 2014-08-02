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
package eu.chepy.audiokit.core.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import eu.chepy.audiokit.ui.utils.MusicConnector;

public class CallStateBroadcastReceiver extends BroadcastReceiver {

	public static final String TAG = "CallStateBroadcastReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		PhoneStateListener phoneListener = new PhoneStateListener() {
			
			public void onCallStateChanged(int state, String incomingNumber) {
				Log.d(TAG, "callStateChanged() call");
				
				switch (state) {
				case TelephonyManager.CALL_STATE_IDLE:
					MusicConnector.doCallManageIdle();
					break;
				case TelephonyManager.CALL_STATE_OFFHOOK:
					MusicConnector.doCallManageOffHook();
					break;
				case TelephonyManager.CALL_STATE_RINGING:
					MusicConnector.doCallManageRinging();
					break;
				}
			}
		};
		
		TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
	}

}
