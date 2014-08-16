/*
 * PlayerViewHelper.java
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
package eu.chepy.audiokit.ui.utils;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.mobeta.android.dslv.DragSortListView;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.IPlayerServiceListener;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.activities.CarModeActivity;
import eu.chepy.audiokit.ui.activities.LibraryMainActivity;
import eu.chepy.audiokit.ui.adapter.LibraryAdapter;
import eu.chepy.audiokit.ui.adapter.LibraryAdapterFactory;
import eu.chepy.audiokit.ui.utils.uil.ProviderImageDownloader;
import eu.chepy.audiokit.ui.views.RepeatingImageButton;
import eu.chepy.audiokit.utils.LogUtils;

public class PlayerViewHelper implements
        LoaderManager.LoaderCallbacks<Cursor>,
        DragSortListView.DropListener, DragSortListView.DragScrollProfile,
        AdapterView.OnItemClickListener,
        View.OnCreateContextMenuListener {

    public final static String TAG = PlayerViewHelper.class.getSimpleName();

    /*
        Ui state
     */
    private static final String SAVED_STATE_ACTION_BAR_IS_VISIBLE = "saved_state_action_bar_visibility";

    private static boolean saved_state_playlist_is_visible = false;



    /*
        Host activity
     */
    private ActionBarActivity hostActivity;

    private LibraryAdapter adapter;

    private Cursor playlistCursor;

    private int[] requestedFields = new int[] {
            AbstractMediaProvider.SONG_ID,
            AbstractMediaProvider.SONG_TITLE,
            AbstractMediaProvider.SONG_ARTIST,
            AbstractMediaProvider.PLAYLIST_ENTRY_POSITION,
            AbstractMediaProvider.SONG_VISIBLE,
    };

    private int[] sortFields = new int[] {
            AbstractMediaProvider.PLAYLIST_ENTRY_POSITION
    };

    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_ENTRY_POSITION = 3;

    private static final int COLUMN_SONG_VISIBLE = 4;



    /*
        Player Ui
     */
    private View imageViewContainer;

    private View playlistContainer;

    private ImageView artImageView;

    private ImageView bluredImageView;

    private TextView titleTextView;

    private TextView artistTextView;

    private TextView timeTextView;

    private ImageButton repeatButton;

    private ImageButton playButton;

    private ImageButton shuffleButton;

    private SeekBar progressBar;

    private DragSortListView playlist;

    private ImageButton playlistButton;

    private TextView playlistLengthTextView;



    private SlidingUpPanelLayout slidingUpPanelLayout;

    /*
        Player Ui logic
     */
    private boolean updateSeekBar = true;

    private static int autostop;

    private static Handler autostopHandler;

    private static Runnable autostopTask;



    /*
        Ad mob
     */
    private InterstitialAd interstitial = null;



    public PlayerViewHelper(ActionBarActivity fragmentActivity) {
        hostActivity = fragmentActivity;

        if (PlayerApplication.isFreemium()) {
            int noDisplayCounter = PlayerApplication.adDisplayGetCounter();

            if (noDisplayCounter >= 5) {
                interstitial = new InterstitialAd(hostActivity);
                interstitial.setAdUnitId("ca-app-pub-3216044483473621/6665880790");

                AdRequest adRequest = new AdRequest.Builder()
                        .addTestDevice("2A8AFDBBC128894B872A1F3DAE11358D") // Nexus 5
                        .addTestDevice("EA2776551264A5F012EAD8016CCAFD67") // LG GPad
                        .build();

                interstitial.loadAd(adRequest);
                interstitial.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        PlayerApplication.adDisplayReset();
                    }
                });
            }
            else {
                PlayerApplication.adDisplayInc();
            }
        }

        imageViewContainer = hostActivity.findViewById(R.id.square_view_container);
        playlistContainer = hostActivity.findViewById(R.id.playlist_container);

        artImageView = (ImageView) hostActivity.findViewById(R.id.player_art);

        bluredImageView = (ImageView) hostActivity.findViewById(R.id.player_art_blured);

        titleTextView = (TextView) hostActivity.findViewById(R.id.audio_player_track_name);
        artistTextView = (TextView) hostActivity.findViewById(R.id.audio_player_artist_name);
        timeTextView = (TextView) hostActivity.findViewById(R.id.audio_player_time);
        playlistLengthTextView = (TextView) hostActivity.findViewById(R.id.playlist_track_count);

        repeatButton = (ImageButton) hostActivity.findViewById(R.id.audio_player_repeat);
        repeatButton.setOnClickListener(MusicConnector.repeatClickListener);

        final RepeatingImageButton prevButton = (RepeatingImageButton) hostActivity.findViewById(R.id.audio_player_prev);
        prevButton.setOnClickListener(MusicConnector.prevClickListener);

        playButton = (ImageButton) hostActivity.findViewById(R.id.audio_player_play);
        playButton.setOnClickListener(new MusicConnector.PlayClickListenerImpl(hostActivity));

        final RepeatingImageButton nextButton = (RepeatingImageButton) hostActivity.findViewById(R.id.audio_player_next);
        nextButton.setOnClickListener(MusicConnector.nextClickListener);

        shuffleButton = (ImageButton) hostActivity.findViewById(R.id.audio_player_shuffle);
        shuffleButton.setOnClickListener(MusicConnector.shuffleClickListener);

        progressBar = (SeekBar) hostActivity.findViewById(R.id.progress);
        progressBar.setOnSeekBarChangeListener(progressBarOnChangeListener);

        final LibraryAdapter.LibraryAdapterContainer container = new LibraryAdapter.LibraryAdapterContainer() {
            @Override
            public Activity getActivity() {
                return hostActivity;
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
                new int[] {
                        COLUMN_SONG_ID,
                        COLUMN_SONG_TITLE,
                        COLUMN_SONG_ARTIST,
                        COLUMN_SONG_VISIBLE
                });
        adapter.setTransparentBackground(true);

        playlist = (DragSortListView) hostActivity.findViewById(R.id.dragable_list_base);
        if (playlist != null) {
            playlist.setEmptyView(hostActivity.findViewById(R.id.dragable_list_empty));
            playlist.setAdapter(adapter);
            playlist.setDropListener(this);
            playlist.setDragScrollProfile(this);
            playlist.setOnCreateContextMenuListener(this);
            playlist.setOnItemClickListener(this);
        }

        playlistButton = (ImageButton) hostActivity.findViewById(R.id.playlist_show);
        if (playlistButton != null) {
            playlistButton.setOnClickListener(playlistToggleVisibilityClickListener);
        }

        slidingUpPanelLayout = (SlidingUpPanelLayout) hostActivity.findViewById(R.id.sliding_layout);
        if (slidingUpPanelLayout != null) {
            //slidingUpPanelLayout.setShadowDrawable(hostActivity.getResources().getDrawable(R.drawable.above_shadow));
            slidingUpPanelLayout.setDragView(hostActivity.findViewById(R.id.upper_panel));
            slidingUpPanelLayout.setPanelSlideListener(panelSlideListener);
        }

        final ImageButton songOptionsButton = (ImageButton) hostActivity.findViewById(R.id.song_options);
        if (songOptionsButton != null) {
            songOptionsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doShowOverflowMenu(v);
                }
            });
        }
    }

    public void onActivityDestroy() {
        if (interstitial != null && interstitial.isLoaded() && PlayerApplication.isFreemium()) {
            interstitial.show();
        }
    }

    public int changePlaylistCursor(Cursor cursor) {
        int queuePosition = MusicConnector.getCurrentPlaylistPosition();

        if (adapter != null) {
            adapter.changeCursor(cursor);

            if (playlist != null) {
                playlist.invalidateViews();
            }

            if (cursor != null) {
                if (playlist != null) {
                    playlist.setSelection(queuePosition);
                }

                playerServiceListener.fullUiUpdate();
            }
        }
        return queuePosition;
    }

    protected void doShowOverflowMenu(final View v) {
        final PopupMenu popupMenu = new PopupMenu(hostActivity, v);

        final MenuItem share = popupMenu.getMenu().add(Menu.NONE, 1, 1, R.string.menu_label_share);
        share.setIcon(R.drawable.ic_action_share_dark);
        share.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");

                final String mediaTitle = currentMediaTitle();
                final String mediaArtist = currentMediaArtist();

                if (!TextUtils.isEmpty(mediaTitle) && !TextUtils.isEmpty(mediaArtist)) {
                    final String sharingText = String.format(PlayerApplication.context.getString(R.string.share_body), mediaTitle, mediaArtist);
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, sharingText);
                    hostActivity.startActivity(Intent.createChooser(sharingIntent, PlayerApplication.context.getString(R.string.share_via)));
                }
                return true;
            }
        });

/*
        if (PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex].getMediaManagerType() == AbstractMediaManager.LOCAL_MEDIA_MANAGER) {
            final MenuItem ringtone = popupMenu.getMenu().add(Menu.NONE, 2, 2, R.string.menu_label_ringtone);
            ringtone.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                 // Step 1: transcoding in app external storage (ogg is the most supported format)
                    ProgressDialog progressDialog = new ProgressDialog(hostActivity);

                    final LocalTranscoder localTranscoder = new LocalTranscoder(hostActivity, progressDialog, (int)(playlistCursor.getLong(COLUMN_SONG_DURATION) / 1000));
                    localTranscoder.load(playlistCursor.getString(COLUMN_SONG_URI));
                    localTranscoder.start();
                 // Step 2: setting the transcoded file as ringtone.
                    return true;
                }
            });
        }
*/
        final MenuItem carMode = popupMenu.getMenu().add(Menu.NONE, 3, 3, R.string.menu_label_toggle_car_mode);
        carMode.setIcon(R.drawable.ic_action_car);
        carMode.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final Intent carmodeIntent = new Intent(hostActivity, CarModeActivity.class);
                hostActivity.startActivity(carmodeIntent);
                return true;
            }
        });

        if (MusicConnector.isPlaying()) {
            final MenuItem autoPause = popupMenu.getMenu().add(Menu.NONE, 4, 4, autostop != 0 ?
                    String.format(hostActivity.getString(R.string.menu_label_delayed_pause_in), PlayerApplication.formatSecs(autostop)) : // format minutes (not secs)
                    hostActivity.getString(R.string.menu_label_delayed_pause));
            autoPause.setCheckable(true);
            autoPause.setChecked(autostop != 0);
            autoPause.setIcon(R.drawable.ic_action_alarm);
            autoPause.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (autoPause.isChecked()) {
                        autostop = 0;
                        if (autostopHandler != null && autostopTask != null) {
                            autostopHandler.removeCallbacks(autostopTask);
                            autostopHandler = null;
                            autostopTask = null;
                        }
                    } else {
                        final TimePickerDialog tpd = new TimePickerDialog(hostActivity,
                                new TimePickerDialog.OnTimeSetListener() {

                                    @Override
                                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                        autostop = hourOfDay * 60 + minute;
                                        autostopTask = new Runnable() {
                                            @Override
                                            public void run() {
                                                MusicConnector.doStopAction();
                                            }
                                        };

                                        autostopHandler = new Handler();
                                        autostopHandler.postDelayed(autostopTask, autostop * 60000);
                                    }
                                }, 0, 0, true
                        );

                        tpd.setTitle(R.string.label_autostop);
                        tpd.show();

                    }

                    return true;
                }
            });
        }


        popupMenu.show();
    }

    public void registerServiceListener() {
        try {
            PlayerApplication.playerService.registerPlayerCallback(playerServiceListener);
            int queuePosition = MusicConnector.getCurrentPlaylistPosition();

            if (playlist != null) {
                playlist.setSelection(queuePosition);
            }

            if (playlistCursor != null) {
                playerServiceListener.fullUiUpdate();
            }
        } catch (final RemoteException remoteException) {
            LogUtils.LOGException(TAG, "registerServiceListener()", 0, remoteException);
        }
    }

    public void unregisterServiceListener() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.unregisterPlayerCallback(playerServiceListener);
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "unregisterServiceListener()", 0, remoteException);
            }
        }
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



    /*
        DragScrollProfile implementation
     */
    @Override
    public float getSpeed(float w, long t) {
        if (w > 0.8F) {
            return adapter.getCount() / 0.001F;
        }
        return 10.0F * w;
    }

    /*
        DropListener implementation
     */
    @Override
    public void drop(int from, int to) {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.queueMove(from, to);
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "drop()", 0, remoteException);
            }
        }

        hostActivity.getSupportLoaderManager().restartLoader(0, null, this);
    }



    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return PlayerApplication.buildMediaLoader(
                PlayerApplication.playerManagerIndex,
                requestedFields,
                sortFields,
                null,
                AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursor == null) {
            return;
        }

        if (cursor.getCount() <= 0) {
            slidingUpPanelLayout.collapsePanel();
            slidingUpPanelLayout.hidePanel();
        }
        else {
            slidingUpPanelLayout.showPanel();
        }

        playlistCursor = cursor;
        changePlaylistCursor(cursor);

        if (playlistLengthTextView != null) {
            int count = playlistCursor.getCount();
            playlistLengthTextView.setText(hostActivity.getResources().getQuantityString(R.plurals.label_track_position, count, count));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        changePlaylistCursor(null);
        playlistCursor = null;
    }



    /*
        Context menu implementation
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        doOnCreateContextMenu(menu);
    }

    public boolean onContextItemSelected(android.view.MenuItem item) {
        return doOnContextItemSelected(item.getItemId()) || hostActivity.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        doPlayAction();
    }


    // return true if actionbar is visible
    public boolean restoreInstanceState(Bundle savedInstanceState) {
        return savedInstanceState == null || savedInstanceState.getBoolean(SAVED_STATE_ACTION_BAR_IS_VISIBLE, true);
    }

    public void saveInstanceState(Bundle outState, boolean actionbarIsVisible) {
        outState.putBoolean(SAVED_STATE_ACTION_BAR_IS_VISIBLE, actionbarIsVisible);
    }

    public void refreshViews() {
        if (playlistButton != null) {
            if (saved_state_playlist_is_visible) {
                playlistButton.setImageResource(R.drawable.ic_action_list_2);
            } else {
                playlistButton.setImageResource(R.drawable.ic_action_list_2_off);
            }
        }

        if (hostActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && playlistContainer != null) {
            playlistContainer.setVisibility(saved_state_playlist_is_visible ? View.VISIBLE : View.INVISIBLE);
            if (imageViewContainer != null) {
                imageViewContainer.setVisibility(!saved_state_playlist_is_visible ? View.VISIBLE : View.INVISIBLE);
            }
        }

        playerServiceListener.doPlaylistPositionUpdate();
    }

    protected void doOnCreateContextMenu(Menu menu) {
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_CLEAR, 5, R.string.context_menu_remove_all);
        menu.add(Menu.NONE, PlayerApplication.CONTEXT_MENUITEM_DELETE, 6, R.string.context_menu_remove);
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
            try {
                PlayerApplication.playerService.queueSetPosition(playlistCursor.getPosition());
                PlayerApplication.playerService.play();
                return true;
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "onContextItemSelected()", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "onContextItemSelected()", 0);
        }

        return false;
    }

    protected boolean doPlayNextAction() {
        if (PlayerApplication.playerService != null) {
            try {
                final int queueSize = PlayerApplication.playerService.queueGetSize();
                final int queuePosition = PlayerApplication.playerService.queueGetPosition();

                PlayerApplication.playerService.queueAdd(playlistCursor.getString(COLUMN_SONG_ID));
                PlayerApplication.playerService.queueMove(queueSize, queuePosition + 1);
                return true;
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doPlayNextAction", 0, remoteException);
            }
        }

        return false;
    }

    protected boolean doAddToQueueAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.queueAdd(playlistCursor.getString(COLUMN_SONG_ID));
                return true;
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doAddToQueueAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doAddToQueueAction", 0);
        }
        return false;
    }

    protected boolean doAddToPlaylistAction() {
        return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, playlistCursor.getString(COLUMN_SONG_ID), MusicConnector.songs_sort_order);
    }

    protected boolean doClearAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.stop();
                PlayerApplication.playerService.queueClear();
                return true;
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doClearAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doClearAction", 0);
        }
        return false;
    }

    protected boolean doDeleteAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.queueRemove(playlistCursor.getInt(COLUMN_ENTRY_POSITION));
                return true;
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doDeleteAction", 0, remoteException);
            }
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

        if (playlist != null) {
            int first = playlist.getFirstVisiblePosition();
            int last = playlist.getLastVisiblePosition();

            if (position < first) {
                playlist.setSelection(position);
            } else if (position >= last) {
                playlist.setSelection(1 + position - (last - first));
            }
        }

        if (!playlistCursor.isClosed()) {
            playlistCursor.moveToPosition(position);
        }
    }

    /*
        Player service listener
     */
    private PlayerServiceListenerImpl playerServiceListener = new PlayerServiceListenerImpl();

    public final class PlayerServiceListenerImpl extends IPlayerServiceListener.Stub {

        @Override
        public void onShuffleModeChanged() throws RemoteException {
            hostActivity.runOnUiThread(shuffleButtonUpdateRunnable);
        }

        @Override
        public void onRepeatModeChanged() throws RemoteException {
            hostActivity.runOnUiThread(repeatButtonUpdateRunnable);
        }

        @Override
        public void onSeek(final long position) throws RemoteException {
            seekbarRunnable.position = (int) position;
            hostActivity.runOnUiThread(seekbarRunnable);
        }

        @Override
        public void onQueueChanged() throws RemoteException {
            hostActivity.getSupportLoaderManager().restartLoader(0, null, PlayerViewHelper.this);
        }

        @Override
        public void onQueuePositionChanged() throws RemoteException {
            hostActivity.runOnUiThread(songUpdateRunnable);
        }

        @Override
        public void onPlay() throws RemoteException {
            hostActivity.runOnUiThread(playButtonUpdateRunnable);
        }

        @Override
        public void onPause() throws RemoteException {
            hostActivity.runOnUiThread(playButtonUpdateRunnable);
        }

        @Override
        public void onStop() throws RemoteException {
            hostActivity.runOnUiThread(playButtonUpdateRunnable);
        }

        public void fullUiUpdate() {
            hostActivity.runOnUiThread(shuffleButtonUpdateRunnable);
            hostActivity.runOnUiThread(repeatButtonUpdateRunnable);
            hostActivity.runOnUiThread(playButtonUpdateRunnable);
            hostActivity.runOnUiThread(seekbarRunnable);
            hostActivity.runOnUiThread(songUpdateRunnable);
        }

        protected void doPlaylistPositionUpdate() {
            int queuePosition = MusicConnector.getCurrentPlaylistPosition();
            doSetPlaylistPosition(queuePosition);
        }

        protected void doSongUpdate() {
            if (playlistCursor != null && playlistCursor.getCount() > 0 && playlistCursor.getPosition() >= 0) {

                long position = 0;
                int queuePosition = 0;

                if (PlayerApplication.playerService != null) {
                    try {
                        position = PlayerApplication.playerService.getPosition();
                        queuePosition = PlayerApplication.playerService.queueGetPosition();
                    }
                    catch (final RemoteException remoteException) {
                        LogUtils.LOGException(TAG, "doSongUpdate", 0, remoteException);
                    }
                }

                doSetPlaylistPosition(queuePosition);

                titleTextView.setText(playlistCursor.getString(COLUMN_SONG_TITLE));
                artistTextView.setText(playlistCursor.getString(COLUMN_SONG_ARTIST));
                timeTextView.setText(PlayerApplication.formatMSecs(position));

                final String songArtUri =
                        ProviderImageDownloader.SCHEME_URI_PREFIX +
                        ProviderImageDownloader.SUBTYPE_MEDIA + "/" +
                        PlayerApplication.playerManagerIndex + "/" +
                        playlistCursor.getString(COLUMN_SONG_ID);

                PlayerApplication.normalImageLoader.displayImage(songArtUri, artImageView, loaderListener);
            }
            else {
                titleTextView.setText("");
                artistTextView.setText("");
                timeTextView.setText(R.string.label_00_00);
                artImageView.setImageResource(R.drawable.no_art_normal);
                if (bluredImageView != null) {
                    bluredImageView.setImageResource(R.drawable.no_art_normal);
                }
            }
        }

        protected void doRepeatButtonUpdate() {
            if (PlayerApplication.playerService != null) {
                try {
                    switch (PlayerApplication.playerService.getRepeatMode()) {
                        case PlayerService.REPEAT_ALL:
                            repeatButton.setImageResource(R.drawable.ic_action_playback_repeat);
                            break;
                        case PlayerService.REPEAT_CURRENT:
                            repeatButton.setImageResource(R.drawable.ic_action_playback_repeat_1);
                            break;
                        case PlayerService.REPEAT_NONE:
                            repeatButton.setImageResource(R.drawable.ic_action_playback_repeat_off);
                            break;
                    }
                } catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "doRepeatButtonUpdate", 0, remoteException);
                }
            }
            else {
                LogUtils.LOGService(TAG, "doRepeatButtonUpdate", 0);
            }
        }

        protected void doPlayButtonUpdate() {
            if (PlayerApplication.playerService != null) {
                try {
                    if (PlayerApplication.playerService.isPlaying()) {
                        playButton.setImageResource(R.drawable.ic_action_playback_pause);
                        timeTextView.clearAnimation();
                    }
                    else {
                        playButton.setImageResource(R.drawable.ic_action_playback_play);
                        Animation animation = new AlphaAnimation(0.0f, 1.0f);
                        animation.setDuration(100);
                        animation.setStartOffset(400);
                        animation.setRepeatMode(Animation.REVERSE);
                        animation.setRepeatCount(Animation.INFINITE);
                        timeTextView.startAnimation(animation);
                    }

                    long currentPosition = PlayerApplication.playerService.getPosition();

                    if (currentPosition == 0) {
                        onSeek(0);
                    }

                    progressBar.setMax((int) PlayerApplication.playerService.getDuration());
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "doPlayButtonUpdate()", 0, remoteException);
                }
            }
            else {
                LogUtils.LOGService(TAG, "doPlayButtonUpdate()", 0);
            }
        }

        protected void doShuffleButtonUpdate() {
            if (PlayerApplication.playerService != null) {
                try {
                    switch (PlayerApplication.playerService.getShuffleMode()) {
                        case PlayerService.SHUFFLE_AUTO:
                            shuffleButton.setImageResource(R.drawable.ic_action_playback_shuffle);
                            break;
                        case PlayerService.SHUFFLE_NONE:
                            shuffleButton.setImageResource(R.drawable.ic_action_playback_shuffle_off);
                            break;
                    }
                } catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "doShuffleButtonUpdate", 0, remoteException);
                }
            }
            else {
                LogUtils.LOGService(TAG, "doShuffleButtonUpdate", 0);
            }
        }

        private Runnable playButtonUpdateRunnable = new Runnable() {
            public void run() {
                doPlayButtonUpdate();
            }
        };

        private Runnable shuffleButtonUpdateRunnable = new Runnable() {
            public void run() {
                doShuffleButtonUpdate();
            }
        };

        private Runnable repeatButtonUpdateRunnable = new Runnable() {
            public void run() {
                doRepeatButtonUpdate();
            }
        };

        private Runnable songUpdateRunnable = new Runnable() {
            public void run() {
                doPlaylistPositionUpdate();
                doSongUpdate();
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
                progressBar.setProgress(position);
            }

            timeTextView.setText(PlayerApplication.formatMSecs(position));
        }
    }



    final SeekBar.OnSeekBarChangeListener progressBarOnChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                if (PlayerApplication.playerService.isPlaying()) {
                    PlayerApplication.playerService.pause(true);
                    PlayerApplication.playerService.setPosition(seekBar.getProgress());
                    PlayerApplication.playerService.play();
                }
                else {
                    PlayerApplication.playerService.setPosition(seekBar.getProgress());
                }
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "progressBarOnChangeListener.onStopTrackingTouch()", 0, remoteException);
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



    final SlidingUpPanelLayout.PanelSlideListener panelSlideListener = new SlidingUpPanelLayout.PanelSlideListener() {

        @Override
        public void onPanelSlide(View view, float v) {
            if (v >= 0.8) {
                if (hostActivity.getSupportActionBar().isShowing()) {
                    hostActivity.getSupportActionBar().hide();

                    if (hostActivity instanceof LibraryMainActivity) {
                        ((LibraryMainActivity)hostActivity).setSwipeMenuEnabled(false);
                    }
                }
            } else {
                if (!hostActivity.getSupportActionBar().isShowing()) {
                    hostActivity.getSupportActionBar().show();

                    if (hostActivity instanceof LibraryMainActivity) {
                        ((LibraryMainActivity)hostActivity).setSwipeMenuEnabled(true);
                    }
                }
            }
        }

        @Override
        public void onPanelCollapsed(View view) {

        }

        @Override
        public void onPanelExpanded(View view) {

        }

        @Override
        public void onPanelAnchored(View view) {

        }

        @Override
        public void onPanelHidden(View view) {

        }
    };



    final View.OnClickListener playlistToggleVisibilityClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            saved_state_playlist_is_visible = !saved_state_playlist_is_visible;
            refreshViews();
        }
    };


    final private ImageLoadingListener loaderListener = new ImageLoadingListener() {
        @Override
        public void onLoadingStarted(String imageUri, View view) {
        }

        @Override
        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
            artImageView.setImageResource(R.drawable.no_art_normal);
            if (bluredImageView != null) {
                bluredImageView.setImageResource(R.drawable.no_art_normal);
            }
        }

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
            if (bluredImageView != null) {
                bluredImageView.setImageBitmap(loadedImage);
            }
        }

        @Override
        public void onLoadingCancelled(String imageUri, View view) {

        }
    };
}
