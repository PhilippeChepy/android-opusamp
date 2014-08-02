package eu.chepy.audiokit.ui.activities;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.actionbarsherlock.app.SherlockFragmentActivity;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.views.VerticalSeekBar;

public class SoundEffectsActivity extends SherlockFragmentActivity {

    private CheckBox equalizerEnabledCheckbox;

    private LinearLayout bandContainerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_equalizer);

        equalizerEnabledCheckbox = (CheckBox) findViewById(R.id.equalizer_enabled);
        bandContainerLayout = (LinearLayout) findViewById(R.id.equalizer_bands);

        for (int bandIndex = 0 ; bandIndex < 10 ; bandIndex++) {
            VerticalSeekBar.inflate(this, R.layout.view_equalizer_band, bandContainerLayout);
        }
    }
}
