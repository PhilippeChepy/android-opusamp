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
import android.widget.CheckBox;
import android.widget.LinearLayout;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.ui.views.VerticalSeekBar;

public class SoundEffectsActivity extends ActionBarActivity {

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
