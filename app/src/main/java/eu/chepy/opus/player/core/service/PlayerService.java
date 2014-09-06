/*
 * PlayerService.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.opus.player.core.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import eu.chepy.opus.player.core.NotificationHelper;
import eu.chepy.opus.player.core.RemoteControlClientHelper;
import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.ui.utils.PlayerApplication;
import eu.chepy.opus.player.ui.utils.uil.ProviderImageDownloader;
import eu.chepy.opus.player.ui.widgets.Widget4x1;
import eu.chepy.opus.player.ui.widgets.Widget4x2;
import eu.chepy.opus.player.utils.LogUtils;


public class PlayerService extends Service implements AbstractMediaManager.Player.OnProviderCompletionListener {

	private final static String TAG = "PlayerService";



    /*
        Command
     */
    public static final String COMMAND_KEY = "command";

    public static final String ACTION_APPWIDGET_COMMAND = "eu.chepy.opus.player.service.APPWIDGET";

    public static final String ACTION_NOTIFICATION_COMMAND = "eu.chepy.opus.player.service.NOTIFICATION";

    public static final String ACTION_TOGGLEPAUSE = "eu.chepy.opus.player.TOGGLE_PAUSE";

    public static final String ACTION_NEXT = "eu.chepy.opus.player.NEXT";

    public static final String ACTION_PREVIOUS = "eu.chepy.opus.player.PREVIOUS";

    public static final String ACTION_STOP = "eu.chepy.opus.player.STOP";



    /*
        Shuffle modes
     */
    public static final int SHUFFLE_NONE = 0;

    public static final int SHUFFLE_AUTO = 1;



    /*
        Repeat modes
     */
    public static final int REPEAT_NONE = 0;

    public static final int REPEAT_CURRENT = 1;

    public static final int REPEAT_ALL = 2;



    /*

     */
    private WakeLock wakelock;

    private NotificationHelper notificationHelper;

    private RemoteControlClientHelper remoteControlClient;

    private AudioManager audioManager;

    private final PlayerServiceImpl playerServiceImpl = new PlayerServiceImpl();

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private BroadcastReceiver broadcastReceiver;

    public boolean hasNotification;

    private final Widget4x1 widgetMedium = Widget4x1.getInstance();

    private final Widget4x2 widgetLarge = Widget4x2.getInstance();



    int repeatMode = REPEAT_NONE;

    int shuffleMode = SHUFFLE_NONE;



    private Cursor playlist = null;

    private ArrayList<Integer> playlistOrder;

    private int playlistOrderIndex;



    private AbstractMediaManager.Media currentMedia;

    private AbstractMediaManager.Media nextMedia;

    private Lock preloadingMutex = new ReentrantLock();

    private Lock notifyMutex = new ReentrantLock();



    /*

     */
    private int[] requestedFields = new int[] {
            AbstractMediaManager.Provider.SONG_ID,
            AbstractMediaManager.Provider.SONG_URI,
            AbstractMediaManager.Provider.SONG_TITLE,
            AbstractMediaManager.Provider.SONG_ARTIST,
            AbstractMediaManager.Provider.SONG_ALBUM,
            AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION,
            AbstractMediaManager.Provider.SONG_DURATION
    };

    private int[] sortOrder = new int[] {
            AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
    };

    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_URI = 1;

    private static final int COLUMN_SONG_TITLE = 2;

    private static final int COLUMN_SONG_ARTIST = 3;

    private static final int COLUMN_SONG_ALBUM = 4;

    private static final int COLUMN_PLAYLIST_ENTRY_POSITION = 5;

    private static final int COLUMN_SONG_DURATION = 6;



    /*
     * Current song informations
     */
    private String currentTrack = null;

    private String currentArtist = null;

    private String currentAlbum = null;

    private String currentArtUri = null;

    private Bitmap currentArt = null;



    @Override
    public void onCodecCompletion() {
        LogUtils.LOGD(TAG, "completed in JAVA!");
        /* Playback is stopped */
        boolean hasLooped = false;

        try {
            if (repeatMode != REPEAT_CURRENT) {
                hasLooped = playerServiceImpl.next();
            }
            else {
                playerServiceImpl.setPosition(0);
            }

            if (!hasLooped || repeatMode != REPEAT_NONE) {
                playerServiceImpl.play();
            }
        }
        catch (final RemoteException remoteException) {
            LogUtils.LOGException(TAG, "onCodecCompletion", 0, remoteException);
        }

        if (hasLooped) {
            playerServiceImpl.notifyStop(); /* cannot play anymore */
        }
    }

    @Override
    public void onCodecTimestampUpdate(long newPosition) {
        /* Playing, request for timestamp update */
        playerServiceImpl.notifyTimestampUpdate(newPosition);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "whatever");

        notificationHelper = new NotificationHelper(this);
        remoteControlClient = new RemoteControlClientHelper();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


        setAudioListener();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        hasNotification = false;
        repeatMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, REPEAT_NONE);
        shuffleMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, SHUFFLE_NONE);
        int position = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, 0);

        reloadPlaylist();

        if (playlist != null && playlist.getCount() > 0) {
            try {
                if (position < 0) {
                    position = 0;
                }

                playerServiceImpl.queueSetPosition(position);
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "onStartCommand", 0, remoteException);
            }
        }

        try {
            playerServiceImpl.notifyProviderChanged();
        } catch (final RemoteException remoteException) {
            LogUtils.LOGException(TAG, "onStartCommand", 1, remoteException);
        }

        doUpdateWidgets();

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                doManageCommandIntent(intent);
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_NOTIFICATION_COMMAND));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            doManageCommandIntent(intent);
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(broadcastReceiver);

        stopForeground(true);
        hasNotification = false;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return playerServiceImpl;
    }



    class PlayerServiceImpl extends IPlayerService.Stub {

        private RemoteCallbackList<IPlayerServiceListener> playerServiceListeners;

        public PlayerServiceImpl() {
            playerServiceListeners = new RemoteCallbackList<IPlayerServiceListener>();
        }

        @Override
        public void play() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            if (isPlaying()) {
                return;
            }

            if (currentMedia == null) {
                preloadingMutex.lock();
                    if (nextMedia != null && nextMedia.getMediaUri().equals(playlist.getString(COLUMN_SONG_URI))) {
                        LogUtils.LOGI(TAG, "Using preloaded content");
                        currentMedia = nextMedia;
                        nextMedia = null;

                        preloadMediaAsync();
                    }
                    else if (playlist.getCount() > 0) {
                        LogUtils.LOGI(TAG, "NOT using preloaded content");
                        if (nextMedia != null) {
                            player.finalizeContent(nextMedia);
                            nextMedia = null;
                        }

                        preloadMediaAsync();

                        currentMedia = loadMedia(playlist.getString(COLUMN_SONG_URI));

                    }
                preloadingMutex.unlock();
            }

            if (currentMedia != null) {
                player.playerSetContent(currentMedia);
                player.playerPlay();
                wakelock.acquire();
                notifyPlay();
            }

            notifyPlay();
        }

        @Override
        public void pause(boolean keepNotification) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            if (player.playerIsPlaying()) {
                player.playerPause(true);
                notifyPause(keepNotification);
                wakelock.release();
            }

            notifyPause(true);
        }

        @Override
        public void stop() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            player.playerStop();
            currentMedia = unloadMedia(currentMedia);
            nextMedia = unloadMedia(nextMedia);

            notifyStop();
            if (wakelock.isHeld()) {
                wakelock.release();
            }
        }

        @Override
        public boolean next() throws RemoteException {
            boolean looped = false;
            currentMedia = unloadMedia(currentMedia);

            if (playlist != null && playlist.getCount() > 1) {
                looped = doMoveToNextPosition();
                notifySetQueuePosition();
            }

            return looped;
        }

        @Override
        public boolean prev() throws RemoteException {
            boolean looped = false;
            currentMedia = unloadMedia(currentMedia);

            if (playlist != null && playlist.getCount() > 1) {
                looped = doMoveToPrevPosition();
                notifySetQueuePosition();
            }

            return looped;
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            return player.playerIsPlaying();
        }

        @Override
        public long getDuration() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            return player.playerGetDuration();
        }

        @Override
        public long getPosition() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            return player.playerGetPosition();
        }

        @Override
        public void setPosition(long position) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            player.playerSeek(position);
            notifyTimestampUpdate(position);
        }

        @Override
        public int getShuffleMode() throws RemoteException {
            return shuffleMode;
        }

        @Override
        public void setShuffleMode(int mode) throws RemoteException {
            shuffleMode = mode;
            if (shuffleMode == SHUFFLE_AUTO) {
                if (getRepeatMode() == REPEAT_CURRENT) {
                    setRepeatMode(REPEAT_ALL);
                }
            }

            notifyShuffleChange();
        }

        @Override
        public int getRepeatMode() throws RemoteException {
            return repeatMode;
        }

        @Override
        public void setRepeatMode(int mode) throws RemoteException {
            repeatMode = mode;
            if (repeatMode == REPEAT_CURRENT) {
                if (getShuffleMode() != SHUFFLE_NONE) {
                    setShuffleMode(SHUFFLE_NONE);
                }
            }

            notifyRepeatChange();
        }

        @Override
        public void queueAdd(String media) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();
            provider.playlistAdd(null, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, media, 0, null);

            int position = playlist.getPosition();

            reloadPlaylist();

            if (!playlist.moveToPosition(position)) {
                playlist.moveToLast();
            }

            if (playlist.getCount() == 1) {
                play();
            }

            notifyQueueChanged();
        }

        @Override
        public void queueMove(int indexFrom, int indexTo) throws RemoteException {
            if (indexFrom == indexTo) {
                return;
            }

            int position = playlist.getPosition();
            if (indexFrom < position && indexTo >= position) {
                position--;
            }
            else if (indexFrom > position && indexTo <= position) {
                position++;
            }
            else if (indexFrom == position) {
                position = indexTo;
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();
            provider.playlistMove(null, indexFrom, indexTo);

            reloadPlaylist();

            if (!playlist.moveToPosition(position)) {
                playlist.moveToLast();
            }
        }

        @Override
        public void queueRemove(int entry) throws RemoteException {
            int position = playlist.getPosition();
            if (entry < position) {
                position--;
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();
            provider.playlistRemove(null, entry);

            boolean wasPlaying = isPlaying();

            if (position == entry) {
                wasPlaying = false;
                stop();
                currentMedia = unloadMedia(currentMedia);
            }
            else {
                pause(true);
            }

            reloadPlaylist();

            if (playlist.getCount() == 0) {
                notifySetQueuePosition();
            }
            else {
                if (!playlist.moveToPosition(position)) {
                    playlist.moveToLast();
                }

                notifySetQueuePosition();
                if (wasPlaying) {
                    play();
                }
            }

            notifyQueueChanged();
        }

        @Override
        public void queueClear() throws RemoteException {
            if (isPlaying()) {
                stop();
                currentMedia = unloadMedia(currentMedia);
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();

            provider.playlistClear(null);
            reloadPlaylist();

            notifyQueueChanged();
        }

        @Override
        public void queueReload() throws RemoteException {
            final int position = playlist.getPosition();

            boolean wasPlaying = isPlaying();

            if (wasPlaying) {
                pause(true);
            }

            reloadPlaylist();

            if (!playlist.moveToPosition(position)) {
                playlist.moveToFirst();
            }

            if (currentMedia != null && playlist.getCount() > 0) {
                final String mediaUri = currentMedia.getMediaUri();
                if (!TextUtils.isEmpty(mediaUri) && mediaUri.equals(playlist.getString(COLUMN_SONG_URI))) {
                    if (wasPlaying) {
                        play();
                    }
                }
                else {
                    currentMedia = unloadMedia(currentMedia);
                }
            }

            notifyQueueChanged();
        }


        @Override
        public void queueSetPosition(int position) throws RemoteException {
            currentMedia = unloadMedia(currentMedia);
            nextMedia = unloadMedia(nextMedia);

            if (playlist != null && playlist.getCount() > 0) {
                LogUtils.LOGD(TAG, "setQueuePosition() : moving to position " + position);

                if (shuffleMode == SHUFFLE_NONE) {
                    if (playlist.getPosition() != position) {
                        if (!playlist.moveToPosition(position)) {
                            playlist.moveToFirst();
                        }
                    }
                } else if (shuffleMode == SHUFFLE_AUTO) {
                    int currentTrackIndex = playlistOrder.get(playlistOrderIndex);
                    if (currentTrackIndex != position) {
                        int indexOfPosition = playlistOrder.indexOf(position);
                        playlistOrder.set(indexOfPosition, currentTrackIndex);
                        playlistOrder.set(playlistOrderIndex, position);

                        playlist.moveToPosition(position);
                    }
                }

                notifySetQueuePosition();
            }
        }

        @Override
        public int queueGetPosition() throws RemoteException {
            if (playlist == null || playlist.getCount() == 0) {
                return -1;
            }

            if (shuffleMode == SHUFFLE_NONE) {
                return playlist.getInt(COLUMN_PLAYLIST_ENTRY_POSITION);
            }
            else if (shuffleMode == SHUFFLE_AUTO) {
                return playlistOrder.get(playlistOrderIndex);
            }

            return 0;
        }

        @Override
        public int queueGetSize() throws RemoteException {
            return playlist.getCount();
        }

        @Override
        public void registerPlayerCallback(IPlayerServiceListener playerServiceListener) throws RemoteException {
            if (!playerServiceListeners.register(playerServiceListener)) {
                throw new RemoteException();
            }
        }

        @Override
        public void unregisterPlayerCallback(IPlayerServiceListener playerServiceListener) throws RemoteException {
            if (!playerServiceListeners.unregister(playerServiceListener)) {
                throw new RemoteException();
            }
        }

        @Override
        public void notifyProviderChanged() throws RemoteException {
            lockNotify();
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            player.addCompletionListener(PlayerService.this);
            unlockNotify();
        }

        public void notifyTimestampUpdate(long timestamp) {
            lockNotify();
            int count = playerServiceListeners.beginBroadcast();
            try {
                for (int index = 0 ; index < count ; index++) {
                    playerServiceListeners.getBroadcastItem(index).onSeek(timestamp);
                }
            }
            catch (final RemoteException exception) {
                LogUtils.LOGException(TAG, "notifyTimestampUpdate", 0, exception);
            }
            playerServiceListeners.finishBroadcast();
            unlockNotify();
        }

        public void notifyPlay() {
            lockNotify();
            /*
                System ui update
             */
            int audioFocus = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (PlayerApplication.hasICS()) {
                audioFocus = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            remoteControlClient.register(PlayerService.this, audioManager);
            if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (hasNotification) {
                    notificationHelper.goToIdleState(true);
                }
                else {
                    doUpdateRemoteClient();
                    doUpdateNotification();
                    doUpdateWidgets();

                    startForeground(PlayerApplication.NOTIFICATION_PLAY_ID, notificationHelper.getNotification());
                    hasNotification = true;
                }
                remoteControlClient.updateState(true);
            }

            /*
                Client ui update
             */
            int count = playerServiceListeners.beginBroadcast();
            try {
                for (int index = 0 ; index < count ; index++) {
                    playerServiceListeners.getBroadcastItem(index).onPlay();
                }
            }
            catch (final RemoteException exception) {
                LogUtils.LOGException(TAG, "notifyPlay", 0, exception);
            }
            playerServiceListeners.finishBroadcast();
            unlockNotify();
        }

        public void notifyPause(boolean keepNotification) {
            lockNotify();
            /*
                System ui update
             */
            remoteControlClient.updateState(false);

            if (keepNotification) {
                notificationHelper.goToIdleState(false);
            }
            else {
                stopForeground(true);
                hasNotification = false;

                if (PlayerApplication.hasICS()) {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
            }

            doUpdateWidgets();

            /*
                Client ui update
             */
            int count = playerServiceListeners.beginBroadcast();
            try {
                for (int index = 0 ; index < count ; index++) {
                    playerServiceListeners.getBroadcastItem(index).onPause();
                }
            }
            catch (final RemoteException exception) {
                LogUtils.LOGException(TAG, "notifyPause", 0, exception);
            }
            playerServiceListeners.finishBroadcast();
            unlockNotify();
        }

        public void notifyStop() {
            lockNotify();
            /*
                System ui update
             */
            stopForeground(true);
            remoteControlClient.stop();
            remoteControlClient.release();

            doUpdateWidgets();

            if (PlayerApplication.hasICS()) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            hasNotification = false;

            /*
                Client ui update
             */
            int count = playerServiceListeners.beginBroadcast();
            try {
                for (int index = 0 ; index < count ; index++) {
                    playerServiceListeners.getBroadcastItem(index).onStop();
                }
            }
            catch (final RemoteException exception) {
                LogUtils.LOGException(TAG, "notifyStop", 0, exception);
            }
            playerServiceListeners.finishBroadcast();
            unlockNotify();
        }

        private void notifyQueueChanged() {
            lockNotify();
            hasNotification = false;

            doUpdateWidgets();

            final int count = playerServiceListeners.beginBroadcast();
            for (int index = 0 ; index < count; index++) {
                try {
                    playerServiceListeners.getBroadcastItem(index).onQueueChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifyQueueChanged", 0, remoteException);
                }
            }
            playerServiceListeners.finishBroadcast();

            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            final SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
            edit.apply();
            unlockNotify();
        }

        private void notifyShuffleChange() {
            lockNotify();
            final int count = playerServiceListeners.beginBroadcast();
            for (int index = 0 ; index < count; index++) {
                try {
                    playerServiceListeners.getBroadcastItem(index).onShuffleModeChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifyShuffleChange", 0, remoteException);
                }
            }
            playerServiceListeners.finishBroadcast();

            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            final SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, shuffleMode);
            edit.apply();
            unlockNotify();
        }

        private void notifyRepeatChange() {
            lockNotify();
            final int count = playerServiceListeners.beginBroadcast();
            for (int index = 0 ; index < count; index++) {
                try {
                    playerServiceListeners.getBroadcastItem(index).onRepeatModeChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifyRepeatChange", 0, remoteException);
                }
            }
            playerServiceListeners.finishBroadcast();

            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            final SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, repeatMode);
            edit.apply();
            unlockNotify();
        }

        private void notifySetQueuePosition() {
            lockNotify();
            hasNotification = false;

            if (playlist != null && playlist.getPosition() >= 0) {
                currentTrack = playlist.getString(COLUMN_SONG_TITLE);
                currentArtist = playlist.getString(COLUMN_SONG_ARTIST);
                currentAlbum = playlist.getString(COLUMN_SONG_ALBUM);
                //currentArt = null;
                currentArtUri = ProviderImageDownloader.SCHEME_URI_PREFIX + ProviderImageDownloader.SUBTYPE_MEDIA + "/" + PlayerApplication.playerManagerIndex + "/" + playlist.getInt(COLUMN_SONG_ID);

                PlayerApplication.normalImageLoader.loadImage(currentArtUri, (DisplayImageOptions) null, new ImageLoadingListener() {

                    @Override
                    public void onLoadingStarted(String imageUri, View view) {
                    }

                    @Override
                    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                        currentArt = null;
                        doUpdateNotification();
                        doUpdateRemoteClient();
                        doUpdateWidgets();
                    }

                    @Override
                    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                        if (imageUri.equals(currentArtUri)) {
                            currentArt = loadedImage;
                            doUpdateNotification();
                            doUpdateRemoteClient();
                            doUpdateWidgets();
                        }
                    }

                    @Override
                    public void onLoadingCancelled(String imageUri, View view) {
                        currentArt = null;
                        doUpdateNotification();
                        doUpdateRemoteClient();
                        doUpdateWidgets();
                    }
                });
            }
            else {
                currentTrack = null;
                currentArtist = null;
                currentAlbum = null;
                currentArt = null;

                currentArtUri = null;
            }

            doUpdateWidgets();

            final int count = playerServiceListeners.beginBroadcast();
            for (int index = 0 ; index < count; index++) {
                try {
                    playerServiceListeners.getBroadcastItem(index).onQueuePositionChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifySetQueuePosition", 0, remoteException);
                }
            }
            playerServiceListeners.finishBroadcast();

            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            final SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
            edit.apply();
            unlockNotify();
        }
    }

    @TargetApi(14)
    private void setAudioListener() {
        if (PlayerApplication.hasICS()) {
            audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        remoteControlClient.release();
                        try {
                            playerServiceImpl.pause(false);
                        } catch (final Exception exception) {
                            LogUtils.LOGException(TAG, "setAudioListener", 0, exception);
                        }
                    }
                }
            };
        }
    }

    protected void doUpdateWidgets() {
        try {
            boolean isPlaying = playerServiceImpl.isPlaying();

            widgetLarge.notifyChange(PlayerService.this, currentTrack, currentArtist, currentAlbum, currentArt, isPlaying);
            widgetMedium.notifyChange(PlayerService.this, currentTrack, currentArtist, currentAlbum, currentArt, isPlaying);
        }
        catch (final Exception exception) {
            LogUtils.LOGException(TAG, "doUpdateWidgets", 0, exception);
        }
    }

    protected void doUpdateRemoteClient() {
        remoteControlClient.updateMetadata(currentArt, currentTrack, currentArtist, currentAlbum, playlist.getLong(COLUMN_SONG_DURATION));
    }

    protected void doUpdateNotification() {
        notificationHelper.buildNotification(currentAlbum, currentArtist, currentTrack, currentArt);
    }

    protected void doManageCommandIntent(final Intent intent) {
        if (intent.hasExtra(COMMAND_KEY)) {
            final String source = intent.getAction();
            final String action = intent.getStringExtra(PlayerService.COMMAND_KEY);

            boolean isNotificationControl = source.equals(PlayerService.ACTION_NOTIFICATION_COMMAND);
            boolean isWidgetControl = source.equals(PlayerService.ACTION_APPWIDGET_COMMAND);
            boolean isRemoteControl = isNotificationControl || isWidgetControl;

            if (action != null && isRemoteControl) {
                try {
                    if (action.equals(PlayerService.ACTION_PREVIOUS)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(true);
                            playerServiceImpl.prev();
                            playerServiceImpl.play();
                        }
                        else {
                            playerServiceImpl.prev();
                        }
                    } else if (action.equals(PlayerService.ACTION_NEXT)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(true);
                            playerServiceImpl.next();
                            playerServiceImpl.play();
                        }
                        else {
                            playerServiceImpl.next();
                        }
                    } else if (action.equals(PlayerService.ACTION_STOP)) {
                        playerServiceImpl.stop();
                    } else if (action.equals(PlayerService.ACTION_TOGGLEPAUSE)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(isNotificationControl);
                        } else {
                            playerServiceImpl.play();
                        }
                    }
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "doManageCommandIntent", 0, remoteException);
                }
            }
        }
    }

    protected boolean doMoveToNextPosition() {
        boolean looped = false;
        if (shuffleMode == SHUFFLE_NONE) {
            if (!playlist.moveToNext()) {
                playlist.moveToFirst();
                looped = true;
            }
        }
        else if (shuffleMode == SHUFFLE_AUTO) {
            playlistOrderIndex++;
            if (playlistOrderIndex >= playlistOrder.size()) {
                playlistOrderIndex = 0;
                looped = true;
            }

            playlist.moveToPosition(playlistOrder.get(playlistOrderIndex));
        }
        return looped;
    }

    protected boolean doMoveToPrevPosition() {
        boolean looped = false;
        if (shuffleMode == SHUFFLE_NONE) {
            if (!playlist.moveToPrevious()) {
                playlist.moveToLast();
                looped = true;
            }
        }
        else if (shuffleMode == SHUFFLE_AUTO) {
            playlistOrderIndex--;
            if (playlistOrderIndex < 0) {
                playlistOrderIndex = playlistOrder.size() - 1;
                looped = true;
            }

            playlist.moveToPosition(playlistOrder.get(playlistOrderIndex));
        }
        return looped;
    }

    protected void reloadPlaylist() {
        if (playlist != null) {
            playlist.close();
        }

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        playlist = provider.buildCursor(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA,
                requestedFields,
                sortOrder,
                null,
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);

        int playlistLength = playlist != null ? playlist.getCount() : 0;

        playlistOrder = new ArrayList<Integer>();
        for (int playlistIndex = 0 ; playlistIndex < playlistLength ; playlistIndex++) {
            playlistOrder.add(playlistIndex);
        }

        Collections.shuffle(playlistOrder);
        playlistOrderIndex = 0;
    }

    protected AbstractMediaManager.Media unloadMedia(final AbstractMediaManager.Media track) {
        if (track != null) {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            if (currentMedia != null && track.getMediaUri().equals(currentMedia.getMediaUri()) && player.playerIsPlaying()) {
                player.playerStop();
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    player.finalizeContent(track);
                }
            }).start();
        }
        return null;
    }

    protected AbstractMediaManager.Media loadMedia(final String songUri) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaManager.Player player = mediaManager.getPlayer();

        final AbstractMediaManager.Media ret = player.initializeContent(songUri);
        player.preloadContent(ret);
        return ret;
    }

    protected void preloadMediaAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000); // TODO: that's a hack :( preload
                    // TODO: enable preloading at
                    //      get int delay = AbstractMediaProvider.preloadingPreferedDelay();
                    //      if (delay > 0) {
                    //          Thread.sleep(delay);
                    //      }
                    //      else {
                    //          Thread.sleep(currentSong.duration - currentTimestamp - delay);
                    //      }
                }
                catch (final Exception interruptException) {

                }

                doMoveToNextPosition();
                final String songUri = playlist.getString(COLUMN_SONG_URI);
                doMoveToPrevPosition();

                if (nextMedia != null && nextMedia.getMediaUri().equals(songUri)) {
                    LogUtils.LOGD(TAG, "already preloaded : " + songUri);
                }
                else {
                    LogUtils.LOGD(TAG, "preloading : " + songUri);
                    preloadingMutex.lock();
                    nextMedia = loadMedia(songUri);
                    preloadingMutex.unlock();
                }
            }
        }).start();
    }

    protected void lockNotify() {
        notifyMutex.lock();
    }

    protected void unlockNotify() {
        notifyMutex.unlock();
    }
}
