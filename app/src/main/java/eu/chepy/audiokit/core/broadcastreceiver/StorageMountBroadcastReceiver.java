/*
 * StorageMountBroadcastReceiver.java
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
import android.os.RemoteException;
import android.util.Log;

import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.utils.LogUtils;

public class StorageMountBroadcastReceiver extends BroadcastReceiver {

	public static final String TAG = "StorageMountBroadcastReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive()");
		
		if (intent.equals(Intent.ACTION_MEDIA_REMOVED)) {
			if (MusicConnector.playerService != null) {
				try {
					if (MusicConnector.playerService.isPlaying()) {
						MusicConnector.playerService.stop();
					}
				} catch (final RemoteException remoteException) {
					LogUtils.LOGException(TAG, "onReceive()", 0, remoteException);
				}
			}
		}
	}
	
}
