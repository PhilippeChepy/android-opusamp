/*
 * ServiceIntentReceiver.java
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
package eu.chepy.opus.player.core.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import eu.chepy.opus.player.ui.utils.MusicConnector;

public class ServiceIntentReceiver extends BroadcastReceiver {
	public static final String TAG = ServiceIntentReceiver.class.getSimpleName();
	
	@Override
	public void onReceive(Context context, Intent intent) {
		MusicConnector.doManageControlIntent(intent);
	}
}
