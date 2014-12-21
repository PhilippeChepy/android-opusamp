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
import android.support.v4.content.LocalBroadcastManager;
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
import com.squareup.otto.Subscribe;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerEventBus;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.event.MediaChangedEvent;
import net.opusapp.player.core.service.event.MediaCoverChangedEvent;
import net.opusapp.player.core.service.event.PlayStateChangedEvent;
import net.opusapp.player.core.service.event.PlaylistChangedEvent;
import net.opusapp.player.core.service.event.RepeatModeChangedEvent;
import net.opusapp.player.core.service.event.ShuffleModeChangedEvent;
import net.opusapp.player.core.service.event.TimestampChangedEvent;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.activities.settings.EqualizerSettingsActivity;
import net.opusapp.player.ui.activities.settings.FirstRunActivity;
import net.opusapp.player.ui.adapter.LibraryAdapter;
import net.opusapp.player.ui.adapter.LibraryAdapterFactory;
import net.opusapp.player.ui.dialogs.MetadataDialog;
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
            AbstractMediaManager.Provider.SONG_TRACK,
            AbstractMediaManager.Provider.SONG_VISIBLE
    };

    private int[] sortFields = new int[] {
            AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
    };

    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_ENTRY_POSITION = 3;

    private static final int COLUMN_SONG_ART_URI = 4;

    private static final int COLUMN_SONG_TRACK_NUMBER = 5;

    private static final int COLUMN_SONG_VISIBLE = 6;



    /*
        Player Ui
     */
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

    private MenuItem mAutoPause;



    private SlidingUpPanelLayout slidingUpPanelLayout;

    // Player Ui logic
    private boolean updateSeekBar = true;

    protected boolean isFirstRun = false;

    // Current media
    private int mCurrentRepeatMode;

    private int mCurrentShuffleMode;

    private String mCurrentTitle;

    private String mCurrentArtist;

    private int mCurrentQueuePosition;

    private int mCurrentPlayState;


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

        mPlaylistContainer = findViewById(R.id.playlist_container);

        mCoverImageView = (ImageView) findViewById(R.id.player_art);

        mBackgroundCoverImageView = (ImageView) findViewById(R.id.player_art_blured);

        mMediaTitleTextView = (TextView) findViewById(R.id.audio_player_track_name);
        mMediaArtistTextView = (TextView) findViewById(R.id.audio_player_artist_name);
        mElapsedTimeTextView = (TextView) findViewById(R.id.audio_player_time);
        mTotalTimeTextView = (TextView) findViewById(R.id.audio_player_time_total);
        mPlaylistPositionTextView = (TextView) findViewById(R.id.playlist_track_count);

        mRepeatButton = (ImageButton) findViewById(R.id.audio_player_repeat);
        mRepeatButton.setOnClickListener(mRepeatButtonClickListener);

        final RepeatingImageButton prevButton = (RepeatingImageButton) findViewById(R.id.audio_player_prev);
        prevButton.setOnClickListener(mPreviousButtonClickListener);
        prevButton.setRepeatListener(mPreviousButtonRepeatListener, 250);

        mPlayButton = (ImageButton) findViewById(R.id.audio_player_play);
        mPlayButton.setOnClickListener(mPlayButtonClickListener);

        final RepeatingImageButton nextButton = (RepeatingImageButton) findViewById(R.id.audio_player_next);
        nextButton.setOnClickListener(mNextButtonClickListener);
        nextButton.setRepeatListener(mNextButtonRepeatListener, 250);

        mShuffleButton = (ImageButton) findViewById(R.id.audio_player_shuffle);
        mShuffleButton.setOnClickListener(mShuffleButtonClickListener);

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
                        COLUMN_SONG_TRACK_NUMBER,
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
            slidingUpPanelLayout.setDragView(findViewById(R.id.audio_player_upper_panel));
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
            mTotalTimeTextView.setTextColor(PlayerApplication.getAccentColor());
        }

        LogUtils.LOGI(TAG, "registering to bus " + PlayerEventBus.getInstance());
        PlayerEventBus.getInstance().register(mEventListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        PlayerEventBus.getInstance().unregister(mEventListener);

        PlayerApplication.disconnectService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        final MenuItem share = menu.add(Menu.NONE, 1, 1, R.string.menuitem_label_share);
        share.setOnMenuItemClickListener(mShareMenuItemListener);

        final MenuItem audioEffectsMenuItem = menu.add(Menu.NONE, 2, 2, R.string.drawer_item_label_library_soundfx);
        audioEffectsMenuItem.setOnMenuItemClickListener(mAudioFxMenuItemListener);

        final MenuItem carMode = menu.add(Menu.NONE, 3, 3, R.string.menuitem_label_toggle_car_mode);
        carMode.setOnMenuItemClickListener(mCarModeMenuItemListener);


        mAutoPause = menu.add(Menu.NONE, 4, 4, null);
        mAutoPause.setVisible(false);
        mAutoPause.setCheckable(true);
        mAutoPause.setIcon(R.drawable.ic_alarm_grey600_48dp);
        mAutoPause.setOnMenuItemClickListener(mAutoPauseMenuItemListener);

        updateAutoPauseMenuItem();

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
                PlayerApplication.playerMediaManager().getProvider(),
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
        playlistButtonUpdateRunnable.run();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


    public SlidingUpPanelLayout getSlidingPanel() {
        return slidingUpPanelLayout;
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

    private void updateAutoPauseMenuItem() {
        if (mAutoPause != null) {
            if (mCurrentPlayState == PlayerService.PLAYSTATE_PLAYING) {
                long remaining = PlayerApplication.playerService != null ? PlayerApplication.playerService.getAutostopTimestamp() : -1;
                long remainingSecs = 0;
                if (remaining > 0) {
                    remainingSecs = remaining / 1000;
                }

                String autoPauseText =
                        remaining > 0 ?
                                String.format(getString(R.string.menuitem_label_delayed_pause_in), PlayerApplication.formatSecs(remainingSecs)) :
                                getString(R.string.menuitem_label_delayed_pause);

                mAutoPause.setVisible(true);
                mAutoPause.setTitle(autoPauseText);
                mAutoPause.setChecked(remaining > 0);
            }
            else {
                mAutoPause.setVisible(false);
            }
        }
    }

    public boolean hasPlaylist() {
        return playlistCursor != null && playlistCursor.getCount() > 0;
    }


    private Object mEventListener = new Object() {

        // Player service listener
        @SuppressWarnings("unused")
        @Subscribe public void mediaCoverChanged(MediaCoverChangedEvent mediaCoverChangedEvent) {
            final Bitmap bitmap = mediaCoverChangedEvent.getBitmap();

            if (mediaCoverChangedEvent.isAvailable()) {
                mCoverImageView.setImageBitmap(bitmap);
                if (mBackgroundCoverImageView != null) {
                    mBackgroundCoverImageView.setImageBitmap(bitmap);
                }
            }
            else {
                mCoverImageView.setImageResource(R.drawable.no_art_normal);
                if (mBackgroundCoverImageView != null) {
                    mBackgroundCoverImageView.setImageResource(R.drawable.no_art_normal);
                }
            }
        }


        @SuppressWarnings("unused")
        @Subscribe public void playStateChanged(PlayStateChangedEvent playStateChangedEvent) {
            mCurrentPlayState = playStateChangedEvent.getPlayState();

            updateAutoPauseMenuItem();

            if (mCurrentPlayState == PlayerService.PLAYSTATE_PLAYING) {
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
        }

        @SuppressWarnings("unused")
        @Subscribe public void timestampChanged(TimestampChangedEvent timestampChangedEvent) {
            if (updateSeekBar) {
                mProgressSeekBar.setProgress((int) (timestampChangedEvent.getTimestamp() / 100));
            }
            mElapsedTimeTextView.setText(PlayerApplication.formatMSecs(timestampChangedEvent.getTimestamp()));
        }

        @SuppressWarnings("unused")
        @Subscribe public void mediaChanged(MediaChangedEvent mediaChangedEvent) {
            mCurrentQueuePosition = mediaChangedEvent.getQueuePosition();
            mCurrentTitle = mediaChangedEvent.getTitle();
            mCurrentArtist = mediaChangedEvent.getArtist();

            doSetPlaylistPosition(mCurrentQueuePosition);
            mMediaTitleTextView.setText(mCurrentTitle);
            mMediaArtistTextView.setText(mCurrentArtist);
            mTotalTimeTextView.setText(PlayerApplication.formatSecs(mediaChangedEvent.getDuration()));
            mProgressSeekBar.setMax((int) (mediaChangedEvent.getDuration() * 10));
        }

        @SuppressWarnings("unused")
        @Subscribe public void repeatModeChanged(RepeatModeChangedEvent repeatModeChangedEvent) {
            mCurrentRepeatMode = repeatModeChangedEvent.getNewMode();

            switch (mCurrentShuffleMode) {
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

        @SuppressWarnings("unused")
        @Subscribe public void shuffleModeChanged(ShuffleModeChangedEvent shuffleModeChangedEvent) {
            mCurrentShuffleMode = shuffleModeChangedEvent.getNewMode();

            switch (mCurrentShuffleMode) {
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

        @SuppressWarnings("unused")
        @Subscribe public synchronized void playlistChangedEvent(PlaylistChangedEvent playlistChangedEvent) {
            if (playlistChangedEvent.getProviderId() == PlayerApplication.playerMediaManager().getId() && playlistChangedEvent.getPlaylistId() == 0) {
                LogUtils.LOGW(TAG, "playlist length = " + playlistChangedEvent.getPlaylistLength());

                if (slidingUpPanelLayout != null) {
                    if (playlistChangedEvent.getPlaylistLength() <= 0) {
                        slidingUpPanelLayout.collapsePanel();
                        slidingUpPanelLayout.hidePanel();
                    }
                    else if (canShowPanel()) {
                        slidingUpPanelLayout.showPanel();
                    }
                }

                getSupportLoaderManager().restartLoader(0, null, AbstractPlayerActivity.this);
            }
        }
    };

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
                if (mCoverImageView != null) {
                    mCoverImageView.setVisibility(!saved_state_playlist_is_visible ? View.VISIBLE : View.INVISIBLE);
                }
            }

            doSetPlaylistPosition(mCurrentQueuePosition);
        }
    };

    final SeekBar.OnSeekBarChangeListener progressBarOnChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (PlayerApplication.playerService.isPlaying()) {
                PlayerApplication.playerService.pause(true);
                PlayerApplication.playerService.setPosition(seekBar.getProgress() * 100);
                PlayerApplication.playerService.play();
            }
            else {
                PlayerApplication.playerService.setPosition(seekBar.getProgress() * 100);
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
        }
    }

    protected void doOnCreateContextMenu(Menu menu) {
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_CLEAR, 5, R.string.menuitem_label_remove_all);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_DELETE, 6, R.string.menuitem_label_remove);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_DETAIL, 6, R.string.menuitem_label_details);
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
        else if (itemId == PlayerApplication.CONTEXT_MENUITEM_DETAIL) {
            return doDetailsAction();
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
        return PlayerApplication.doContextActionAddToPlaylist(this, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, playlistCursor.getString(COLUMN_SONG_ID), PlayerApplication.library_songs_sort_order);
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

    protected boolean doDetailsAction() {
        final MetadataDialog metadataDialog = new MetadataDialog(this, R.string.alert_dialog_title_media_properties,
                PlayerApplication.playerMediaManager().getProvider(),
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA,
                playlistCursor.getString(COLUMN_SONG_ID));
        metadataDialog.show();
        return true;
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



    // Player UI actions
    public View.OnClickListener mRepeatButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch (mCurrentRepeatMode) {
                case PlayerService.REPEAT_NONE:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_CURRENT);
                    break;
                case PlayerService.REPEAT_CURRENT:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_ALL);
                    break;
                case PlayerService.REPEAT_ALL:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_NONE);
                    break;
            }
        }
    };

    public View.OnClickListener mPreviousButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
            localBroadcastManager.sendBroadcast(PlayerService.CLIENT_PREVIOUS_INTENT);
        }
    };

    public RepeatingImageButton.RepeatListener mPreviousButtonRepeatListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(View v, long duration, int repeatcount) {
            LogUtils.LOGW(TAG, "PREV duration = " + duration + " repeatcount = " + repeatcount);

            if (!PlayerApplication.playerService.isPlaying()) {
                return;
            }

            long currentPosition = mProgressSeekBar.getProgress() * 100;

            if (currentPosition < 2000 || currentPosition > (mProgressSeekBar.getMax() * 100) - 2000) {
                return;
            }

            float multiplier;
            if (repeatcount > 0) {
                multiplier = (float) repeatcount;
            }
            else if (repeatcount > 10) {
                multiplier = 10.0f;
            }
            else {
                multiplier = 1.0f;
            }

            long newPosition = currentPosition + (int) ((float) 2000 * multiplier);


            PlayerApplication.playerService.setPosition(newPosition);
        }
    };

    public View.OnClickListener mNextButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
            localBroadcastManager.sendBroadcast(PlayerService.CLIENT_NEXT_INTENT);
        }
    };

    public RepeatingImageButton.RepeatListener mNextButtonRepeatListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(View v, long duration, int repeatcount) {
            LogUtils.LOGW(TAG, "NEXT duration = " + duration + " repeatcount = " + repeatcount);

            if (!PlayerApplication.playerService.isPlaying()) {
                return;
            }

            long currentPosition = mProgressSeekBar.getProgress() * 100;

            if (currentPosition < 2000 || currentPosition > (mProgressSeekBar.getMax() * 100) - 2000) {
                return;
            }

            float multiplier;
            if (repeatcount > 0) {
                multiplier = (float) repeatcount;
            }
            else if (repeatcount > 10) {
                multiplier = 10.0f;
            }
            else {
                multiplier = 1.0f;
            }

            long newPosition = currentPosition + (int) ((float) 2000 * multiplier);

            PlayerApplication.playerService.setPosition(newPosition);
        }
    };

    public View.OnClickListener mShuffleButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            switch (mCurrentShuffleMode) {
                case PlayerService.SHUFFLE_AUTO:
                    PlayerApplication.playerService.setShuffleMode(PlayerService.SHUFFLE_NONE);
                    break;
                case PlayerService.SHUFFLE_NONE:
                    PlayerApplication.playerService.setShuffleMode(PlayerService.SHUFFLE_AUTO);
                    break;
            }
        }
    };

    public View.OnClickListener mPlayButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
            localBroadcastManager.sendBroadcast(PlayerService.CLIENT_TOGGLE_PLAY_INTENT);
        }
    };



    // Menu item listeners
    private final MenuItem.OnMenuItemClickListener mShareMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");

            if (!TextUtils.isEmpty(mCurrentTitle) && !TextUtils.isEmpty(mCurrentArtist)) {
                final String sharingText = String.format(PlayerApplication.context.getString(R.string.share_body), mCurrentTitle, mCurrentArtist);
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
                    updateAutoPauseMenuItem();
                }
            }
            else {
                final TimePickerDialog tpd = new TimePickerDialog(AbstractPlayerActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {

                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                if (PlayerApplication.playerService != null) {
                                    PlayerApplication.playerService.setAutoStopTimestamp((hourOfDay * 60 + minute) * 60000);
                                    updateAutoPauseMenuItem();
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
