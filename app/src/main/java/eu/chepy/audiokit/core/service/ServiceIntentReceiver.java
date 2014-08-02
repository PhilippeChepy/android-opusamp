package eu.chepy.audiokit.core.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import eu.chepy.audiokit.ui.utils.MusicConnector;

public class ServiceIntentReceiver extends BroadcastReceiver {
	public static final String TAG = ServiceIntentReceiver.class.getSimpleName();
	
	@Override
	public void onReceive(Context context, Intent intent) {
		MusicConnector.doManageControlIntent(intent);
	}
}
