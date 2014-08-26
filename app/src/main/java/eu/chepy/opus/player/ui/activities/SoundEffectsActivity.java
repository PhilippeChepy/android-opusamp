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
package eu.chepy.opus.player.ui.activities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.AbstractMediaPlayer;
import eu.chepy.opus.player.ui.utils.PlayerApplication;
import eu.chepy.opus.player.ui.views.VerticalSeekBar;

public class SoundEffectsActivity extends ActionBarActivity {

    private BandView bandList[] = new BandView[19];

    private String frequencies[] = new String[] {
            "PREAMP",
            "55 Hz", "77 Hz", "110 Hz",
            "156 Hz", "220 Hz", "311 Hz", "440 Hz", "622 Hz", "880 Hz", "1.2 kHz",
            "1.8 kHz", "2.5 kHz", "3.5 kHz", "5 kHz", "7 kHz", "10 kHz", "14 kHz",
            "20 kHz"
    };

    private CheckBox equalizerEnabledCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_equalizer);

        equalizerEnabledCheckbox = (CheckBox) findViewById(R.id.equalizer_enabled);

        final LinearLayout bandContainerLayout = (LinearLayout) findViewById(R.id.equalizer_bands);
        for (int bandIndex = 0 ; bandIndex < 19 ; bandIndex++) {
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

        equalizerEnabledCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyBandState();
            }
        });

        for (int bandIndex = 0 ; bandIndex < 19 ; bandIndex++) {
            bandList[bandIndex].seekBar.setOnSeekBarChangeListener(new BandListener(bandIndex));
        }
    }

    protected void doUpdateBandState() {
        final AbstractMediaPlayer mediaPlayer = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getMediaPlayer();

        equalizerEnabledCheckbox.setChecked(mediaPlayer.equalizerIsEnabled());

        for (int bandIndex = 0 ; bandIndex < 19 ; bandIndex++) {
            bandList[bandIndex].seekBar.setEnabled(equalizerEnabledCheckbox.isChecked());
            bandList[bandIndex].seekBar.setProgress((int)mediaPlayer.equalizerBandGetGain(bandIndex));
        }
    }

    protected void applyBandState() {
        final AbstractMediaPlayer mediaPlayer = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getMediaPlayer();
        mediaPlayer.equalizerSetEnabled(equalizerEnabledCheckbox.isChecked());

        for (int bandIndex = 0 ; bandIndex < 19 ; bandIndex++) {
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
            final AbstractMediaPlayer mediaPlayer = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getMediaPlayer();
            mediaPlayer.equalizerBandSetGain(bandIndex, progress);
            mediaPlayer.equalizerBandSetGain(bandIndex + 19, progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }
}
