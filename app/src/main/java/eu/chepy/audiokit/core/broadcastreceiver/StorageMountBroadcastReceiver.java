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

import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.utils.LogUtils;

public class StorageMountBroadcastReceiver extends BroadcastReceiver {

	public static final String TAG = "StorageMountBroadcastReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)) {
			if (PlayerApplication.playerService != null) {
				try {
					if (PlayerApplication.playerService.isPlaying()) {
                        PlayerApplication.playerService.stop();
					}
				} catch (final RemoteException remoteException) {
					LogUtils.LOGException(TAG, "onReceive()", 0, remoteException);
				}
			}
		}
	}
	
}
