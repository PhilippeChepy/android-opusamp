/*
 * SoundEffectsActivity.java
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
package net.opusapp.player.ui.activities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.VerticalSeekBar;
import net.opusapp.player.utils.LogUtils;

public class SoundEffectsActivity extends ActionBarActivity {

    private BandView bandList[] = new BandView[11];

    private String frequencies[] = new String[] {
            "PREAMP",
            "31.5 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    };

    private CheckBox equalizerEnabledCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sound_effects);
    }

    @Override
    protected void onResume() {
        super.onResume();

        equalizerEnabledCheckbox = (CheckBox) findViewById(R.id.equalizer_enabled);

        final LinearLayout bandContainerLayout = (LinearLayout) findViewById(R.id.equalizer_bands);
        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            final View bandView = LayoutInflater.from(this).inflate(R.layout.view_equalizer_band, bandContainerLayout, false);

            bandList[bandIndex] = new BandView();

            bandList[bandIndex].band = bandView;
            bandList[bandIndex].freq1 = (TextView) bandView.findViewById(R.id.band_freq1);
            bandList[bandIndex].freq2 = (TextView) bandView.findViewById(R.id.band_freq2);

            bandList[bandIndex].seekBar = (VerticalSeekBar) bandView.findViewById(R.id.band_seekbar);
            bandList[bandIndex].seekBar.setMax(28);
            bandList[bandIndex].seekBar.setProgress(14);

            bandList[bandIndex].freq1.setText(frequencies[bandIndex]);
            bandList[bandIndex].freq2.setText(frequencies[bandIndex]);

            bandContainerLayout.addView(bandView);
        }

        doUpdateBandState();

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            bandList[bandIndex].seekBar.setOnSeekBarChangeListener(new BandListener(bandIndex));
        }

        equalizerEnabledCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyBandState();
            }
        });
    }

    protected void doUpdateBandState() {
        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();

        equalizerEnabledCheckbox.setChecked(player.equalizerIsEnabled());

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            bandList[bandIndex].seekBar.setEnabled(equalizerEnabledCheckbox.isChecked());
            bandList[bandIndex].seekBar.setProgress((int) player.equalizerBandGetGain(bandIndex));
        }
    }

    protected void applyBandState() {
        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
        player.equalizerSetEnabled(equalizerEnabledCheckbox.isChecked());

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            bandList[bandIndex].seekBar.setEnabled(equalizerEnabledCheckbox.isChecked());
        }
    }


    class BandView {
        View band;
        TextView freq1;
        TextView freq2;
        VerticalSeekBar seekBar;
    }

    class BandListener implements SeekBar.OnSeekBarChangeListener {

        int bandIndex;

        public BandListener(int bandIndex) {
            this.bandIndex = bandIndex;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
            player.equalizerBandSetGain(bandIndex, progress);
            player.equalizerBandSetGain(bandIndex + 11, progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            LogUtils.LOGW("SoundEffectActivity", "Applying gain");

            final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
            player.equalizerApplyProperties();
        }
    }
}
