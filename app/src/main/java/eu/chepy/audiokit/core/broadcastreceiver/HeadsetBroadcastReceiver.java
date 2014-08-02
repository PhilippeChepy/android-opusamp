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
import android.util.Log;

public class HeadsetBroadcastReceiver extends BroadcastReceiver {
	
	public static final String TAG = "HeadsetBroadcastReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive()");
	}

}
