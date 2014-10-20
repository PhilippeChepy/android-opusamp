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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.VerticalSeekBar;
import net.opusapp.player.utils.LogUtils;

public class SoundEffectsActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = SoundEffectsActivity.class.getSimpleName();



    private BandView bandList[] = new BandView[11];

    private String frequencies[] = new String[] {
            "PREAMP",
            "31.5 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    };

    private CheckBox equalizerEnabledCheckbox;


    // Presets in database
    private ListView listView;

    private SimpleCursorAdapter adapter;

    private Cursor cursor;

    private final static String requestedFields[] = new String[] {
            Entities.EqualizerPresets._ID,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESET_BAND_COUNT,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_PREAMP,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND0,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND1,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND2,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND3,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND4,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND5,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND6,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND7,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND8,
            Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND9
    };

    private final static int COLUMN_PREAMP = 3;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sound_effects);

        listView = (ListView) findViewById(R.id.list_view_base);

        final String from[] = new String[] {
                Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME
        };

        final int to[] = new int[] {
                R.id.line_one
        };

        adapter = new SimpleCursorAdapter(this, R.layout.view_item_single_line_half, null, from, to);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (cursor != null && cursor.getCount() > position) {
                    final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
                    cursor.moveToPosition(position);

                    int preamp = cursor.getInt(COLUMN_PREAMP);
                    bandList[0].seekBar.setProgress(preamp);
                    bandList[0].seekBar.updateThumb();

                    player.equalizerBandSetGain(0, preamp);
                    player.equalizerBandSetGain(11, preamp);

                    for (int bandIndex = 1 ; bandIndex < 11 ; bandIndex++) {
                        int gain = 20 + cursor.getInt(COLUMN_PREAMP + bandIndex);

                        bandList[bandIndex].seekBar.setProgress(gain);
                        bandList[bandIndex].seekBar.updateThumb();

                        player.equalizerBandSetGain(bandIndex, gain);
                        player.equalizerBandSetGain(bandIndex + 11, gain);
                    }

                    player.equalizerApplyProperties();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
        PlayerApplication.saveEqualizerSettings(player);

        for (int managerIndex = 0 ; managerIndex < PlayerApplication.mediaManagers.length ; managerIndex++) {
            if (managerIndex != PlayerApplication.playerManagerIndex) {
                final AbstractMediaManager.Player otherPlayer = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
                PlayerApplication.restoreEqualizerSettings(otherPlayer);
                otherPlayer.equalizerApplyProperties();
            }
        }
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
            bandList[bandIndex].seekBar.setMax(40);
            bandList[bandIndex].seekBar.setProgress(20);

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

        getSupportLoaderManager().initLoader(0, null, this);
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

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {

        return new AbstractSimpleCursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();
                if (database != null) {
                    return database.query(Entities.EqualizerPresets.TABLE_NAME, requestedFields, null, null, null, null, null);
                }
                return null;
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor data) {
        if (data == null) {
            return;
        }

        adapter.changeCursor(data);
        listView.invalidateViews();
        cursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        if (adapter != null) {
            adapter.changeCursor(null);
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
