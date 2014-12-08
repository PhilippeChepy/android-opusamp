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
package net.opusapp.player.ui.activities.settings;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.VerticalSeekBar;
import net.opusapp.player.utils.LogUtils;

public class EqualizerSettingsActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = EqualizerSettingsActivity.class.getSimpleName();



    private BandView mBandList[] = new BandView[11];

    private String mBandFrequencies[] = new String[] {
            "PREAMP",
            "31.5 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    };

    private CheckBox mEqualizerIsActive;


    // Presets in database
    private ListView mListView;

    private SimpleCursorAdapter mAdapter;

    private Cursor mCursor;

    private final static String mRequestedFields[] = new String[] {
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

        mListView = (ListView) findViewById(R.id.list_view_base);

        final String from[] = new String[] {
                Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME
        };

        final int to[] = new int[] {
                R.id.line_one
        };

        mAdapter = new SimpleCursorAdapter(this, R.layout.view_item_single_line_no_anchor, null, from, to, 0);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mCursor != null && mCursor.getCount() > position) {
                    final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
                    mCursor.moveToPosition(position);

                    int preamp = mCursor.getInt(COLUMN_PREAMP);
                    mBandList[0].seekBar.setProgress(preamp);
                    mBandList[0].seekBar.updateThumb();

                    player.equalizerBandSetGain(0, preamp);
                    player.equalizerBandSetGain(11, preamp);

                    for (int bandIndex = 1; bandIndex < 11; bandIndex++) {
                        int gain = 20 + mCursor.getInt(COLUMN_PREAMP + bandIndex);

                        mBandList[bandIndex].seekBar.setProgress(gain);
                        mBandList[bandIndex].seekBar.updateThumb();

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

        mEqualizerIsActive = (CheckBox) findViewById(R.id.equalizer_enabled);

        final LinearLayout bandContainerLayout = (LinearLayout) findViewById(R.id.equalizer_bands);
        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            final View bandView = LayoutInflater.from(this).inflate(R.layout.view_equalizer_band, bandContainerLayout, false);

            mBandList[bandIndex] = new BandView();

            mBandList[bandIndex].band = bandView;
            mBandList[bandIndex].freq1 = (TextView) bandView.findViewById(R.id.band_freq1);
            mBandList[bandIndex].freq2 = (TextView) bandView.findViewById(R.id.band_freq2);

            mBandList[bandIndex].seekBar = (VerticalSeekBar) bandView.findViewById(R.id.band_seekbar);
            mBandList[bandIndex].seekBar.setMax(40);
            mBandList[bandIndex].seekBar.setProgress(20);

            mBandList[bandIndex].freq1.setText(mBandFrequencies[bandIndex]);
            mBandList[bandIndex].freq2.setText(mBandFrequencies[bandIndex]);

            bandContainerLayout.addView(bandView);
        }

        doUpdateBandState();

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            mBandList[bandIndex].seekBar.setOnSeekBarChangeListener(new BandListener(bandIndex));
        }

        mEqualizerIsActive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyBandState();
            }
        });

        getSupportLoaderManager().initLoader(0, null, this);
    }

    protected void doUpdateBandState() {
        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();

        mEqualizerIsActive.setChecked(player.equalizerIsEnabled());

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            mBandList[bandIndex].seekBar.setEnabled(mEqualizerIsActive.isChecked());
            mBandList[bandIndex].seekBar.setProgress((int) player.equalizerBandGetGain(bandIndex));
        }
    }

    protected void applyBandState() {
        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();
        player.equalizerSetEnabled(mEqualizerIsActive.isChecked());

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            mBandList[bandIndex].seekBar.setEnabled(mEqualizerIsActive.isChecked());
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {

        return new AbstractSimpleCursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();
                if (database != null) {
                    return database.query(Entities.EqualizerPresets.TABLE_NAME, mRequestedFields, null, null, null, null, null);
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

        mAdapter.changeCursor(data);
        mListView.invalidateViews();
        mCursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
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
