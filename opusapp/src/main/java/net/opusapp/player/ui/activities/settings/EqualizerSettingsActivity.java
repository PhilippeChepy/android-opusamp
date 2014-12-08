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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import net.opusapp.player.core.service.providers.index.database.OpenHelper;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.dialogs.EditTextDialog;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.VerticalSeekBar;
import net.opusapp.player.utils.LogUtils;

public class EqualizerSettingsActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = EqualizerSettingsActivity.class.getSimpleName();



    //

    private static final int OPTION_MENUITEM_ADD_PRESET = 1;

    private static final int OPTION_MENUITEM_RESTORE_PRESETS = 2;


    private BandView mBandList[] = new BandView[11];

    private String mBandFrequencies[] = new String[] {
            "PREAMP",
            "31.5 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    };

    private CheckBox mEqualizerIsActiveView;


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

    private final static int COLUMN_ID = 0;

    private final static int COLUMN_NAME = 1;

    private final static int COLUMN_PREAMP = 3;



    private final static int MENUITEM_DELETE = 1;



    protected void refresh() {
        getSupportLoaderManager().restartLoader(0, null, EqualizerSettingsActivity.this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sound_effects);

        PlayerApplication.applyActionBar(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mListView = (ListView) findViewById(R.id.list_view_base);

        final String from[] = new String[] {
                Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME
        };

        final int to[] = new int[] {
                R.id.line_one
        };

        mAdapter = new EqualizerPresetsAdapter(this, R.layout.view_item_single_line, null, from, to, 0);
        mListView.setAdapter(mAdapter);
        mListView.setOnCreateContextMenuListener(this);

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

        mEqualizerIsActiveView = (CheckBox) findViewById(R.id.equalizer_enabled);

        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();

        final LinearLayout bandContainerLayout = (LinearLayout) findViewById(R.id.equalizer_bands);
        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            final View bandView = LayoutInflater.from(this).inflate(R.layout.view_equalizer_band, bandContainerLayout, false);

            mBandList[bandIndex] = new BandView();
            mBandList[bandIndex].band = bandView;

            mBandList[bandIndex].freq1 = (TextView) bandView.findViewById(R.id.band_freq1);
            mBandList[bandIndex].freq1.setText(mBandFrequencies[bandIndex]);

            mBandList[bandIndex].freq2 = (TextView) bandView.findViewById(R.id.band_freq2);
            mBandList[bandIndex].freq2.setText(mBandFrequencies[bandIndex]);

            mBandList[bandIndex].bandListener = new BandListener(bandIndex);
            mBandList[bandIndex].seekBar = (VerticalSeekBar) bandView.findViewById(R.id.band_seekbar);
            mBandList[bandIndex].seekBar.setMax(40);
            mBandList[bandIndex].seekBar.setProgress(20);
            mBandList[bandIndex].seekBar.setEnabled(player.equalizerIsEnabled());
            mBandList[bandIndex].seekBar.setProgress((int) player.equalizerBandGetGain(bandIndex));
            mBandList[bandIndex].seekBar.setOnSeekBarChangeListener(mBandList[bandIndex].bandListener);

            bandContainerLayout.addView(bandView);
        }

        mEqualizerIsActiveView.setChecked(player.equalizerIsEnabled());
        mEqualizerIsActiveView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                pushToPlayer();
            }
        });

        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem mSaveMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_ADD_PRESET, 2, R.string.menuitem_label_add_preset);
        mSaveMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_add_black_48dp : R.drawable.ic_add_white_48dp);
        MenuItemCompat.setShowAsAction(mSaveMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        mSaveMenuItem.setOnMenuItemClickListener(mSaveMenuItemClickListener);


        final MenuItem mRestoreMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_RESTORE_PRESETS, 2, R.string.menuitem_label_restore_presets);
        mRestoreMenuItem.setIcon(PlayerApplication.iconsAreDark() ? R.drawable.ic_refresh_black_48dp : R.drawable.ic_refresh_white_48dp);
        MenuItemCompat.setShowAsAction(mRestoreMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        mRestoreMenuItem.setOnMenuItemClickListener(mRestoreMenuItemClickListener);

        return true;
    }


    protected void pushToPlayer() {
        final AbstractMediaManager.Player player = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getPlayer();

        if (mEqualizerIsActiveView.isChecked() && !player.equalizerIsEnabled()) {
            player.equalizerSetEnabled(true);
        }
        else if (!mEqualizerIsActiveView.isChecked() && player.equalizerIsEnabled()) {
            player.equalizerSetEnabled(false);
        }

        for (int bandIndex = 0 ; bandIndex < 11 ; bandIndex++) {
            mBandList[bandIndex].seekBar.setEnabled(mEqualizerIsActiveView.isChecked());
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, MENUITEM_DELETE, 1, R.string.menuitem_label_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == MENUITEM_DELETE) {
            deletePathMenuItemClick(mCursor.getInt(COLUMN_ID));
        }

        return super.onContextItemSelected(item);
    }

    private MenuItem.OnMenuItemClickListener mSaveMenuItemClickListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final EditTextDialog textDialog = new EditTextDialog(EqualizerSettingsActivity.this, R.string.menuitem_label_add_preset);
            textDialog.setPositiveButtonRunnable(new EditTextDialog.ButtonClickListener() {
                @Override
                public void click(EditTextDialog dialog) {
                    final SQLiteOpenHelper openHelper = PlayerApplication.getDatabaseOpenHelper();
                    final SQLiteDatabase database = openHelper.getWritableDatabase();

                    final ContentValues values = new ContentValues();
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESET_NAME, dialog.getText());
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESET_BAND_COUNT, 10);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_PREAMP, mBandList[0].seekBar.getProgress());
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND0, mBandList[1].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND1, mBandList[2].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND2, mBandList[3].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND3, mBandList[4].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND4, mBandList[5].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND5, mBandList[6].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND6, mBandList[7].seekBar.getProgress()- 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND7, mBandList[8].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND8, mBandList[9].seekBar.getProgress() - 20);
                    values.put(Entities.EqualizerPresets.COLUMN_FIELD_PRESERT_BAND9, mBandList[10].seekBar.getProgress() - 20);

                    database.insert(Entities.EqualizerPresets.TABLE_NAME, null, values);
                    refresh();
                }
            });

            textDialog.setNegativeButtonRunnable(new EditTextDialog.ButtonClickListener() {
                @Override
                public void click(EditTextDialog dialog) {
                    // do nothing.
                }
            });


            textDialog.show();
            return true;
        }
    };

    private MenuItem.OnMenuItemClickListener mRestoreMenuItemClickListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            new AlertDialog.Builder(EqualizerSettingsActivity.this)
                    .setTitle(R.string.menuitem_label_restore_presets)
                    .setMessage(R.string.alert_dialog_message_reinitialize_presets)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final SQLiteOpenHelper openHelper = PlayerApplication.getDatabaseOpenHelper();
                            final SQLiteDatabase database = openHelper.getWritableDatabase();

                            Entities.EqualizerPresets.destroyTable(database);
                            Entities.EqualizerPresets.createTable(database);
                            OpenHelper.initDefaultEQPresets(database);

                            refresh();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
            return false;
        }
    };

    protected void deletePathMenuItemClick(int id) {
        final SQLiteOpenHelper openHelper = PlayerApplication.getDatabaseOpenHelper();
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        final String selection = Entities.EqualizerPresets._ID + " = ? ";

        final String selectionArgs[] = new String[] {
                String.valueOf(id)
        };

        if (database != null) {
            database.delete(Entities.EqualizerPresets.TABLE_NAME, selection, selectionArgs);
            refresh();
        }
    }


    class BandView {
        View band;
        TextView freq1;
        TextView freq2;
        VerticalSeekBar seekBar;
        BandListener bandListener;
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

    public class EqualizerPresetsAdapter extends SimpleCursorAdapter {

        public EqualizerPresetsAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);

            final Cursor cursor = (Cursor) getItem(position);
            final GridViewHolder viewholder;

            if (view != null) {
                viewholder = new GridViewHolder(view);
                viewholder.customView = view.findViewById(R.id.card_layout);
                viewholder.contextMenuHandle = view.findViewById(R.id.context_menu_handle);
                view.setTag(viewholder);
            } else {
                viewholder = (GridViewHolder)convertView.getTag();
            }



            viewholder.contextMenuHandle.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    final PopupMenu popupMenu = new PopupMenu(EqualizerSettingsActivity.this, view);
                    final Menu menu = popupMenu.getMenu();

                    final MenuItem menuItem = menu.add(R.string.menuitem_label_delete);
                    menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            cursor.moveToPosition(position);
                            deletePathMenuItemClick(cursor.getInt(0));
                            return true;
                        }
                    });

                    popupMenu.show();
                }
            });

            viewholder.lineOne.setText(cursor.getString(COLUMN_NAME));
            return view;
        }
    }
}
