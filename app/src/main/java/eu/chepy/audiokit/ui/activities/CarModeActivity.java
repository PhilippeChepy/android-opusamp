package eu.chepy.audiokit.ui.activities;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.WindowManager;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class CarModeActivity extends AbstractPlayerActivity {

    public static final String TAG = CarModeActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_car_mode);

        initPlayerView(savedInstanceState, false);
        getSupportLoaderManager().initLoader(0, null, getPlayerView());

        if (PlayerApplication.playerService == null) {
            PlayerApplication.connectService(this);
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

        getPlayerView().onActivityDestroy();
        getPlayerView().unregisterServiceListener();
        PlayerApplication.unregisterServiceCallback(this);

        unbindDrawables(findViewById(R.id.drawer_layout));
        super.onDestroy();
        System.gc();
    }
}
