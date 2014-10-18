/*
 * CarModeActivity.java
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

import net.opusapp.player.R;

public class CarModeActivity extends AbstractPlayerActivity {

    public static final String TAG = CarModeActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_car_mode, null);
    }
}
