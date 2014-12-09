/*
 * AbstractPlayerActivity.java
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

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;

import com.mobeta.android.dslv.DragSortListView;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.activities.settings.EqualizerSettingsActivity;
import net.opusapp.player.ui.activities.settings.FirstRunActivity;
import net.opusapp.player.ui.adapter.LibraryAdapter;
import net.opusapp.player.ui.adapter.LibraryAdapterFactory;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.RepeatingImageButton;
import net.opusapp.player.utils.LogUtils;

public abstract class AbstractPlayerActivity extends OpusActivity implements
        LoaderManager.LoaderCallbacks<Cursor>,
        DragSortListView.DropListener, DragSortListView.DragScrollProfile,
        AdapterView.OnItemClickListener,
        View.OnCreateContextMenuListener,
        ServiceConnection {

    public static final String TAG = AbstractPlayerActivity.class.getSimpleName();

    /*
        Ui state
     */
    private static boolean saved_state_playlist_is_visible = false;



    /*
        Host activity
     */
    private LibraryAdapter adapter;

    private Cursor playlistCursor;

    private int[] requestedFields = new int[] {
            AbstractMediaManager.Provider.SONG_ID,
            AbstractMediaManager.Provider.SONG_TITLE,
            AbstractMediaManager.Provider.SONG_ARTIST,
            AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION,
            AbstractMediaManager.Provider.SONG_ART_URI,
            AbstractMediaManager.Provider.SONG_VISIBLE,
            AbstractMediaManager.Provider.SONG_DURATION
    };

    private int[] sortFields = new int[] {
            AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
    };

    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_ENTRY_POSITION = 3;

    private static final int COLUMN_SONG_ART_URI = 4;

    private static final int COLUMN_SONG_VISIBLE = 5;

    private static final int COLUMN_SONG_DURATION = 6;



    /*
        Player Ui
     */
    private View mCoverContainer;

    private View mPlaylistContainer;


    // Cover
    private ImageView mCoverImageView;

    private ImageView mBackgroundCoverImageView;


    // Song Info
    private TextView mMediaTitleTextView;

    private TextView mMediaArtistTextView;

    private TextView mElapsedTimeTextView;

    private TextView mTotalTimeTextView;

    // Actions & interactions
    private ImageButton mRepeatButton;

    private ImageButton mPlayButton;

    private ImageButton mShuffleButton;

    private SeekBar mProgressSeekBar;

    private DragSortListView mPlaylistListView;

    private ImageButton mSwitchPlaylistButton;

    private TextView mPlaylistPositionTextView;



    private SlidingUpPanelLayout slidingUpPanelLayout;

    // Player Ui logic
    private boolean updateSeekBar = true;

    protected boolean isFirstRun = false;



    protected void onCreate(Bundle savedInstanceState, int layout, int windowFeatures[]) {
        super.onCreate(savedInstanceState);

        if (PlayerApplication.isFirstRun()) {
            isFirstRun = true;
            final Intent intent = new Intent(this, FirstRunActivity.class);
            startActivity(intent);
            finish();
        }

        if (windowFeatures != null) {
            for (int feature : windowFeatures) {
                supportRequestWindowFeature(feature);
            }
        }

        if (AbstractPlayerActivity.this instanceof CarModeActivity) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            final ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
        setContentView(layout);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Actionbar
        PlayerApplication.applyActionBar(this);

        mCoverContainer = findViewById(R.id.square_view_container);
        mPlaylistContainer = findViewById(R.id.playlist_container);

        mCoverImageView = (ImageView) findViewById(R.id.player_art);

        mBackgroundCoverImageView = (ImageView) findViewById(R.id.player_art_blured);

        mMediaTitleTextView = (TextView) findViewById(R.id.audio_player_track_name);
        mMediaArtistTextView = (TextView) findViewById(R.id.audio_player_artist_name);
        mElapsedTimeTextView = (TextView) findViewById(R.id.audio_player_time);
        mTotalTimeTextView = (TextView) findViewById(R.id.audio_player_time_total);
        mPlaylistPositionTextView = (TextView) findViewById(R.id.playlist_track_count);

        mRepeatButton = (ImageButton) findViewById(R.id.audio_player_repeat);
        mRepeatButton.setOnClickListener(MusicConnector.repeatClickListener);

        final RepeatingImageButton prevButton = (RepeatingImageButton) findViewById(R.id.audio_player_prev);
        prevButton.setOnClickListener(MusicConnector.prevClickListener);

        mPlayButton = (ImageButton) findViewById(R.id.audio_player_play);
        mPlayButton.setOnClickListener(new MusicConnector.PlayClickListenerImpl(this));

        final RepeatingImageButton nextButton = (RepeatingImageButton) findViewById(R.id.audio_player_next);
        nextButton.setOnClickListener(MusicConnector.nextClickListener);

        mShuffleButton = (ImageButton) findViewById(R.id.audio_player_shuffle);
        mShuffleButton.setOnClickListener(MusicConnector.shuffleClickListener);

        mProgressSeekBar = (SeekBar) findViewById(R.id.progress);
        mProgressSeekBar.setOnSeekBarChangeListener(progressBarOnChangeListener);

        final LibraryAdapter.LibraryAdapterContainer container = new LibraryAdapter.LibraryAdapterContainer() {
            @Override
            public Activity getActivity() {
                return AbstractPlayerActivity.this;
            }

            @Override
            public PopupMenu.OnMenuItemClickListener getOnPopupMenuItemClickListener(final int position) {
                return new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        playlistCursor.moveToPosition(position);
                        return doOnContextItemSelected(menuItem.getItemId());
                    }
                };
            }

            @Override
            public void createMenu(Menu menu, int position) {
                playlistCursor.moveToPosition(position);
                doOnCreateContextMenu(menu);
            }
        };

        adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_PLAYLIST_DETAILS, LibraryAdapter.PLAYER_MANAGER,
                new int[]{
                        COLUMN_SONG_ID,
                        COLUMN_SONG_TITLE,
                        COLUMN_SONG_ARTIST,
                        COLUMN_SONG_ART_URI,
                        COLUMN_SONG_VISIBLE
                });
        adapter.setTransparentBackground(true);

        mPlaylistListView = (DragSortListView) findViewById(R.id.dragable_list_playlist);
        if (mPlaylistListView != null) {
            mPlaylistListView.setEmptyView(findViewById(R.id.dragable_list_empty_playlist));
            mPlaylistListView.setAdapter(adapter);
            mPlaylistListView.setDropListener(this);
            mPlaylistListView.setDragScrollProfile(this);
            mPlaylistListView.setOnCreateContextMenuListener(this);
            mPlaylistListView.setOnItemClickListener(this);
        }

        mSwitchPlaylistButton = (ImageButton) findViewById(R.id.playlist_show);
        if (mSwitchPlaylistButton != null) {
            mSwitchPlaylistButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saved_state_playlist_is_visible = !saved_state_playlist_is_visible;
                    runOnUiThread(playlistButtonUpdateRunnable);
                }
            });
        }

        slidingUpPanelLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        if (slidingUpPanelLayout != null) {
            slidingUpPanelLayout.setDragView(findViewById(R.id.upper_panel));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerApplication.connectService(this);

        if (mElapsedTimeTextView != null) {
            mElapsedTimeTextView.setTextColor(PlayerApplication.getAccentColor());
        }

        if (mTotalTimeTextView != null) {
            mTotalTimeTextView.setTextColor(PlayerApplication.getBackgroundColor());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        PlayerApplication.disconnectService(this);
        if (PlayerApplication.playerService != null) { // Avoid NPE while killing process (in dev only).
            PlayerApplication.playerService.unregisterPlayerCallback(playerServiceListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        final MenuItem share = menu.add(Menu.NONE, 1, 1, R.string.menuitem_label_share);
        share.setOnMenuItemClickListener(mShareMenuItemListener);

        final MenuItem audioEffectsMenuItem = menu.add(Menu.NONE, 2, 2, R.string.drawer_item_label_library_soundfx);
        audioEffectsMenuItem.setOnMenuItemClickListener(mAudioFxMenuItemListener);

        final MenuItem carMode = menu.add(Menu.NONE, 3, 3, R.string.menuitem_label_toggle_car_mode);
        carMode.setOnMenuItemClickListener(mCarModeMenuItemListener);

        if (MusicConnector.isPlaying()) {

            long remaining = PlayerApplication.playerService != null ? PlayerApplication.playerService.getAutostopTimestamp() : -1;
            long remainingSecs = 0;
            if (remaining > 0) {
                remainingSecs = remaining / 1000;
            }

            final MenuItem autoPause = menu.add(Menu.NONE, 4, 4, remaining > 0 ?
                    String.format(getString(R.string.menuitem_label_delayed_pause_in), PlayerApplication.formatSecs(remainingSecs)) : // format minutes (not secs)
                    getString(R.string.menuitem_label_delayed_pause));
            autoPause.setCheckable(true);
            autoPause.setChecked(remaining > 0);
            autoPause.setIcon(R.drawable.ic_alarm_grey600_48dp);
            autoPause.setOnMenuItemClickListener(mAutoPauseMenuItemListener);
        }

        return true;
    }


    // DragScrollProfile implementation
    @Override
    public float getSpeed(float w, long t) {
        if (w > 0.8F) {
            return adapter.getCount() / 0.001F;
        }
        return 10.0F * w;
    }

    // DropListener implementation
    @Override
    public void drop(int from, int to) {
        if (PlayerApplication.playerService != null) {
            PlayerApplication.playerService.queueMove(from, to);
        }

        getSupportLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return PlayerApplication.buildMediaLoader(
                PlayerApplication.playerManagerIndex,
                requestedFields,
                sortFields,
                null,
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);
    }

    protected boolean canShowPanel() {
        return true;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursor == null) {
            return;
        }

        if (slidingUpPanelLayout != null) {
            if (cursor.getCount() <= 0) {
                slidingUpPanelLayout.collapsePanel();
                slidingUpPanelLayout.hidePanel();
            }
            else if (canShowPanel()) {
                slidingUpPanelLayout.showPanel();
            }
        }

        playlistCursor = cursor;
        changePlaylistCursor();

        if (mPlaylistPositionTextView != null) {
            int count = playlistCursor.getCount();
            mPlaylistPositionTextView.setText(getResources().getQuantityString(R.plurals.label_track_position, count, count));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        playlistCursor = null;
        changePlaylistCursor();
    }

    /*
        Context menu implementation
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        if (view == mPlaylistListView) {
            doOnCreateContextMenu(menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return doOnContextItemSelected(item.getItemId());
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        doPlayAction();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        getSupportLoaderManager().restartLoader(0, null, this);

        PlayerApplication.playerService.registerPlayerCallback(playerServiceListener);

        playerServiceListener.playButtonUpdateRunnable.run();
        playerServiceListener.repeatButtonUpdateRunnable.run();
        playerServiceListener.shuffleButtonUpdateRunnable.run();
        playerServiceListener.onCoverLoaded(PlayerApplication.playerService.getCurrentCover());
        playlistButtonUpdateRunnable.run();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


    public SlidingUpPanelLayout getSlidingPanel() {
        return slidingUpPanelLayout;
    }

    public String currentMediaTitle() {
        if (playlistCursor == null || playlistCursor.getCount() == 0) {
            return null;
        }

        playlistCursor.moveToPosition(MusicConnector.getCurrentPlaylistPosition());
        return playlistCursor.getString(COLUMN_SONG_TITLE);
    }

    public String currentMediaArtist() {
        if (playlistCursor == null || playlistCursor.getCount() == 0) {
            return null;
        }

        playlistCursor.moveToPosition(MusicConnector.getCurrentPlaylistPosition());
        return playlistCursor.getString(COLUMN_SONG_ARTIST);
    }

    @Override
    public void onBackPressed() {
        if (getSlidingPanel() != null && getSlidingPanel().isPanelExpanded()) {
            getSlidingPanel().collapsePanel();
        }
        else {
            super.onBackPressed();
        }
    }

    public boolean hasPlaylist() {
        return playlistCursor != null && playlistCursor.getCount() > 0;
    }



    // Player service listener
    private PlayerServiceStateListenerImpl playerServiceListener = new PlayerServiceStateListenerImpl();

    public final class PlayerServiceStateListenerImpl implements PlayerService.PlayerServiceStateListener {

        @Override
        public void onPlay() {
            runOnUiThread(playButtonUpdateRunnable);
        }

        @Override
        public void onPause() {
            runOnUiThread(playButtonUpdateRunnable);
        }

        @Override
        public void onStop() {
            runOnUiThread(playButtonUpdateRunnable);
        }

        @Override
        public void onSeek(final long position) {
            seekbarRunnable.position = (int) position;
            runOnUiThread(seekbarRunnable);
        }

        @Override
        public void onShuffleModeChanged() {
            runOnUiThread(shuffleButtonUpdateRunnable);
        }

        @Override
        public void onRepeatModeChanged() {
            runOnUiThread(repeatButtonUpdateRunnable);
        }

        @Override
        public void onQueueChanged() {
            getSupportLoaderManager().restartLoader(0, null, AbstractPlayerActivity.this);
        }

        @Override
        public void onQueuePositionChanged() {
            runOnUiThread(songUpdateRunnable);
        }

        @Override
        public void onCoverLoaded(final Bitmap bitmap) {
            if (bitmap == null) {
                mCoverImageView.setImageResource(R.drawable.no_art_normal);
                if (mBackgroundCoverImageView != null) {
                    mBackgroundCoverImageView.setImageResource(R.drawable.no_art_normal);
                }
            }
            else {
                // TODO: create Runnable once.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCoverImageView.setImageBitmap(bitmap);
                        if (mBackgroundCoverImageView != null) {
                            mBackgroundCoverImageView.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        }

        private Runnable playButtonUpdateRunnable = new Runnable() {

            @Override
            public void run() {
                if (PlayerApplication.playerService != null) {
                    if (PlayerApplication.playerService.isPlaying()) {
                        mPlayButton.setImageResource(R.drawable.ic_pause_grey600_48dp);
                        mElapsedTimeTextView.clearAnimation();
                    }
                    else {
                        mPlayButton.setImageResource(R.drawable.ic_play_arrow_grey600_48dp);
                        Animation animation = new AlphaAnimation(0.0f, 1.0f);
                        animation.setDuration(100);
                        animation.setStartOffset(400);
                        animation.setRepeatMode(Animation.REVERSE);
                        animation.setRepeatCount(Animation.INFINITE);
                        mElapsedTimeTextView.startAnimation(animation);
                    }

                    long currentPosition = PlayerApplication.playerService.getPosition();

                    if (currentPosition == 0) {
                        onSeek(0);
                    }

                    mProgressSeekBar.setMax((int) PlayerApplication.playerService.getDuration());
                }
                else {
                    LogUtils.LOGService(TAG, "doPlayButtonUpdate()", 0);
                }
            }
        };

        private Runnable shuffleButtonUpdateRunnable = new Runnable() {

            @Override
            public void run() {
                if (PlayerApplication.playerService != null) {
                    switch (PlayerApplication.playerService.getShuffleMode()) {
                        case PlayerService.SHUFFLE_AUTO:
                            mShuffleButton.setImageResource(R.drawable.ic_shuffle_black_48dp);
                            mShuffleButton.setColorFilter(PlayerApplication.getBackgroundColor());
                            break;
                        case PlayerService.SHUFFLE_NONE:
                            mShuffleButton.setImageResource(R.drawable.ic_shuffle_grey600_48dp);
                            mShuffleButton.setColorFilter(null);
                            break;
                    }
                }
                else {
                    LogUtils.LOGService(TAG, "doShuffleButtonUpdate", 0);
                }
            }
        };

        private Runnable repeatButtonUpdateRunnable = new Runnable() {

            @Override
            public void run() {
                if (PlayerApplication.playerService != null) {
                    switch (PlayerApplication.playerService.getRepeatMode()) {
                        case PlayerService.REPEAT_ALL:
                            mRepeatButton.setImageResource(R.drawable.ic_repeat_black_48dp);
                            mRepeatButton.setColorFilter(PlayerApplication.getBackgroundColor());
                            break;
                        case PlayerService.REPEAT_CURRENT:
                            mRepeatButton.setImageResource(R.drawable.ic_repeat_one_black_48dp);
                            mRepeatButton.setColorFilter(PlayerApplication.getBackgroundColor());
                            break;
                        case PlayerService.REPEAT_NONE:
                            mRepeatButton.setImageResource(R.drawable.ic_repeat_grey600_48dp);
                            mRepeatButton.setColorFilter(null);
                            break;
                    }
                }
                else {
                    LogUtils.LOGService(TAG, "doRepeatButtonUpdate", 0);
                }
            }
        };

        private Runnable songUpdateRunnable = new Runnable() {

            @Override
            public void run() {
                int queuePosition = MusicConnector.getCurrentPlaylistPosition();
                long position = 0;

                if (playlistCursor != null && queuePosition < playlistCursor.getCount()) {
                    doSetPlaylistPosition(queuePosition);
                    mMediaTitleTextView.setText(playlistCursor.getString(COLUMN_SONG_TITLE));
                    mMediaArtistTextView.setText(playlistCursor.getString(COLUMN_SONG_ARTIST));
                    mTotalTimeTextView.setText(PlayerApplication.formatSecs(playlistCursor.getLong(COLUMN_SONG_DURATION)));
                }
                else {
                    mMediaTitleTextView.setText("");
                    mMediaArtistTextView.setText("");
                    mTotalTimeTextView.setText(PlayerApplication.formatMSecs(0));
                }

                if (PlayerApplication.playerService != null) {
                    mElapsedTimeTextView.setText(PlayerApplication.formatMSecs(PlayerApplication.playerService.getPosition()));
                }
                else {
                    mElapsedTimeTextView.setText(PlayerApplication.formatMSecs(0));
                }




                seekbarRunnable.position = (int) position;
                runOnUiThread(seekbarRunnable);
            }
        };

        private SeekbarRunnable seekbarRunnable = new SeekbarRunnable();
    }



    /*
        Seek bar helper classes
     */
    public class SeekbarRunnable implements Runnable {
        public int position;

        @Override
        public void run() {
            if (updateSeekBar) {
                mProgressSeekBar.setProgress(position);
            }

            mElapsedTimeTextView.setText(PlayerApplication.formatMSecs(position));
        }
    }

    private Runnable playlistButtonUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSwitchPlaylistButton != null) {
                if (saved_state_playlist_is_visible) {
                    mSwitchPlaylistButton.setImageResource(R.drawable.ic_queue_music_black_36dp);
                    mSwitchPlaylistButton.setColorFilter(PlayerApplication.getBackgroundColor());
                } else {
                    mSwitchPlaylistButton.setImageResource(R.drawable.ic_queue_music_grey600_36dp);
                    mSwitchPlaylistButton.setColorFilter(null);
                }
            }

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && mPlaylistContainer != null) {
                mPlaylistContainer.setVisibility(saved_state_playlist_is_visible ? View.VISIBLE : View.INVISIBLE);
                if (mCoverContainer != null) {
                    mCoverContainer.setVisibility(!saved_state_playlist_is_visible ? View.VISIBLE : View.INVISIBLE);
                }
            }

            int queuePosition = MusicConnector.getCurrentPlaylistPosition();
            doSetPlaylistPosition(queuePosition);
        }
    };

    final SeekBar.OnSeekBarChangeListener progressBarOnChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (PlayerApplication.playerService.isPlaying()) {
                PlayerApplication.playerService.pause(true);
                PlayerApplication.playerService.setPosition(seekBar.getProgress());
                PlayerApplication.playerService.play();
            }
            else {
                PlayerApplication.playerService.setPosition(seekBar.getProgress());
            }
            updateSeekBar = true;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            updateSeekBar = false;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }
    };

    public void changePlaylistCursor() {
        if (adapter != null) {
            adapter.changeCursor(playlistCursor);
            if (mPlaylistListView != null) {
                mPlaylistListView.invalidateViews();
            }

            if (playlistCursor != null && playlistCursor.getCount() > 0) {
                playerServiceListener.songUpdateRunnable.run();
            }
        }
    }

    protected void doOnCreateContextMenu(Menu menu) {
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_CLEAR, 5, R.string.menuitem_label_remove_all);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_DELETE, 6, R.string.menuitem_label_remove);
    }

    protected boolean doOnContextItemSelected(int itemId) {
        if (itemId == PlayerApplication.CONTEXT_MENUITEM_PLAY) {
            return doPlayAction();
        }
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_PLAY_NEXT) {
            return doPlayNextAction();
        }
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_ADD_TO_QUEUE) {
            return doAddToQueueAction();
        }
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST) {
            return doAddToPlaylistAction();
        }
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_CLEAR) {
            return doClearAction();
        }
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_DELETE) {
            return doDeleteAction();
        }

        return false;
    }

    protected boolean doPlayAction() {
        if (PlayerApplication.playerService != null) {
            PlayerApplication.playerService.stop();
            PlayerApplication.playerService.queueSetPosition(playlistCursor.getPosition());
            PlayerApplication.playerService.play();
            return true;
        }
        else {
            LogUtils.LOGService(TAG, "onContextItemSelected()", 0);
        }

        return false;
    }

    protected boolean doPlayNextAction() {
        if (PlayerApplication.playerService != null) {
            final int queueSize = PlayerApplication.playerService.queueGetSize();
            final int queuePosition = PlayerApplication.playerService.queueGetPosition();

            PlayerApplication.playerService.queueAdd(playlistCursor.getString(COLUMN_SONG_ID));
            PlayerApplication.playerService.queueMove(queueSize, queuePosition + 1);
            return true;
        }

        return false;
    }

    protected boolean doAddToQueueAction() {
        if (PlayerApplication.playerService != null) {
            PlayerApplication.playerService.queueAdd(playlistCursor.getString(COLUMN_SONG_ID));
            return true;
        }
        else {
            LogUtils.LOGService(TAG, "doAddToQueueAction", 0);
        }
        return false;
    }

    protected boolean doAddToPlaylistAction() {
        return MusicConnector.doContextActionAddToPlaylist(this, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, playlistCursor.getString(COLUMN_SONG_ID), MusicConnector.songs_sort_order);
    }

    protected boolean doClearAction() {
        if (PlayerApplication.playerService != null) {
            PlayerApplication.playerService.stop();
            PlayerApplication.playerService.queueClear();
            PlayerApplication.playerService.queueReload();
            return true;
        }
        else {
            LogUtils.LOGService(TAG, "doClearAction", 0);
        }
        return false;
    }

    protected boolean doDeleteAction() {
        if (PlayerApplication.playerService != null) {
            PlayerApplication.playerService.queueRemove(playlistCursor.getInt(COLUMN_ENTRY_POSITION));
            return true;
        }
        else {
            LogUtils.LOGService(TAG, "doDeleteAction", 0);
        }
        return false;
    }

    protected void doSetPlaylistPosition(int position) {
        if (playlistCursor == null || position < 0 || position >= playlistCursor.getCount()) {
            return;
        }

        adapter.setPosition(position);
        adapter.notifyDataSetChanged();

        if (mPlaylistListView != null) {
            int first = mPlaylistListView.getFirstVisiblePosition();
            int last = mPlaylistListView.getLastVisiblePosition();

            if (position < first) {
                mPlaylistListView.setSelection(position);
            } else if (position >= last) {
                mPlaylistListView.setSelection(1 + position - (last - first));
            }
        }

        if (!playlistCursor.isClosed()) {
            playlistCursor.moveToPosition(position);
        }
    }



    // Menu item listeners
    private final MenuItem.OnMenuItemClickListener mShareMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");

            final String mediaTitle = currentMediaTitle();
            final String mediaArtist = currentMediaArtist();

            if (!TextUtils.isEmpty(mediaTitle) && !TextUtils.isEmpty(mediaArtist)) {
                final String sharingText = String.format(PlayerApplication.context.getString(R.string.share_body), mediaTitle, mediaArtist);
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, sharingText);
                startActivity(Intent.createChooser(sharingIntent, PlayerApplication.context.getString(R.string.share_via)));
            }
            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener mAudioFxMenuItemListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            startActivity(new Intent(PlayerApplication.context, EqualizerSettingsActivity.class));
            return true;
        }
    };


    private final MenuItem.OnMenuItemClickListener mCarModeMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final Intent carmodeIntent = new Intent(AbstractPlayerActivity.this, CarModeActivity.class);
            startActivity(carmodeIntent);
            return true;
        }
    };

    private final MenuItem.OnMenuItemClickListener mAutoPauseMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item.isChecked()) {
                if (PlayerApplication.playerService != null) {
                    PlayerApplication.playerService.setAutoStopTimestamp(-1);
                }
            }
            else {
                final TimePickerDialog tpd = new TimePickerDialog(AbstractPlayerActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                if (PlayerApplication.playerService != null) {
                                    PlayerApplication.playerService.setAutoStopTimestamp((hourOfDay * 60 + minute) * 60000);
                                }
                            }
                        }, 0, 0, true
                );

                tpd.setTitle(R.string.label_autostop);
                tpd.show();

            }

            return true;
        }
    };
}
