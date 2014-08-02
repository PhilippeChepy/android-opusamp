package eu.chepy.audiokit.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.ui.utils.MusicConnector;

public class CarModeActivity extends AbstractPlayerActivity {

    public static final String TAG = CarModeActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_car_mode);

        initPlayerView(savedInstanceState, false);
        getSupportLoaderManager().initLoader(0, null, getPlayerView());

        if (MusicConnector.playerService == null) {
            final Context context = getApplicationContext();

            if (context != null) {
                final Intent playerService = new Intent(context, PlayerService.class);
                context.bindService(playerService, this, Context.BIND_AUTO_CREATE);
                context.startService(playerService);
            }
        }
        else {
            getPlayerView().registerServiceListener();
        }

        System.gc();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        getSupportLoaderManager().destroyLoader(0);

        getPlayerView().unregisterServiceListener();
        unbindDrawables(findViewById(R.id.carmode_layout));
        System.gc();
        Log.w(TAG, "onDestroy()");
    }
}
