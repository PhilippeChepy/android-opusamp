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
package net.opusapp.player.core.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.View;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import net.opusapp.player.R;
import net.opusapp.player.core.NotificationHelper;
import net.opusapp.player.core.RemoteControlClientHelper;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.widgets.AbstractAppWidget;
import net.opusapp.player.ui.widgets.AppWidget4x1;
import net.opusapp.player.ui.widgets.AppWidget4x2;
import net.opusapp.player.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class PlayerService extends Service implements AbstractMediaManager.Player.OnProviderCompletionListener {

	private final static String TAG = PlayerService.class.getSimpleName();



    // Command
    public static final String COMMAND_KEY = "net.opusapp.player.core.service.COMMAND_KEY";

    public static final String ACTION_APPWIDGET_COMMAND = "net.opusapp.player.core.service.ACTION_APPWIDGET_COMMAND";

    public static final String ACTION_NOTIFICATION_COMMAND = "net.opusapp.player.core.service.ACTION_NOTIFICATION_COMMAND";

    public static final String ACTION_CLIENT_COMMAND = "net.opusapp.player.core.service.ACTION_CLIENT_COMMAND";

    public static final String ACTION_TOGGLEPAUSE = "net.opusapp.player.core.service.ACTION_TOGGLEPAUSE";

    public static final String ACTION_PLAY = "net.opusapp.player.core.service.ACTION_PLAY";

    public static final String ACTION_PAUSE = "net.opusapp.player.core.service.ACTION_PAUSE";

    public static final String ACTION_NEXT = "net.opusapp.player.core.service.ACTION_NEXT";

    public static final String ACTION_PREVIOUS = "net.opusapp.player.core.service.ACTION_PREVIOUS";

    public static final String ACTION_STOP = "net.opusapp.player.core.service.ACTION_STOP";



    // Shuffle modes
    public static final int SHUFFLE_NONE = 0;

    public static final int SHUFFLE_AUTO = 1;



    // Repeat modes
    public static final int REPEAT_NONE = 0;

    public static final int REPEAT_CURRENT = 1;

    public static final int REPEAT_ALL = 2;



    /*

     */
    private WakeLock mPlaybackWakeLock;

    private ExecutorService mUiUpdateExecutor;

    private ExecutorService mMediaManagementExecutor;

    private NotificationHelper mNotificationHelper;

    private RemoteControlClientHelper mRemoteControlClient;

    private AudioManager mAudioManager;

    private final PlayerServiceImpl playerServiceImpl = new PlayerServiceImpl();

    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener;

    private BroadcastReceiver mCommandbroadcastReceiver;

    private BroadcastReceiver mHeadsetBroadcastReceiver;

    public boolean hasNotification;

    private final AppWidget4x1 mWidgetMedium = AppWidget4x1.getInstance();

    private final AppWidget4x2 mWidgetLarge = AppWidget4x2.getInstance();



    int mRepeatMode = REPEAT_NONE;

    int mShuffleMode = SHUFFLE_NONE;



    private AbstractMediaManager.Media[] mPlaylist = null;

    private List<Integer> mShuffledPlaylistIndexList;

    private int mShuffledPlaylistIndex;

    private int mPlaylistIndex;

    private Lock notifyMutex = new ReentrantLock();



    /*
     * Current song informations
     */
    private Bitmap currentArt = null;



    @Override
    public void onCodecCompletion() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LogUtils.LOGD(TAG, "completed track");
                try {
                    switch (mRepeatMode) {
                        case REPEAT_ALL:
                            playerServiceImpl.next();
                            playerServiceImpl.play();
                            break;
                        case REPEAT_CURRENT:
                            playerServiceImpl.setPosition(0);
                            playerServiceImpl.play();
                            break;
                        case REPEAT_NONE:
                            if (!playerServiceImpl.next()) {
                                playerServiceImpl.play();
                            }
                            else { /* cannot play anymore */
                                playerServiceImpl.stop();
                                playerServiceImpl.notifyTimestampUpdate(0);
                            }

                            break;
                    }
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "onCodecCompletion", 0, remoteException);
                }
            }
        }).start();
    }

    @Override
    public void onCodecTimestampUpdate(long newPosition) {
        /* Playing, request for timestamp update */
        playerServiceImpl.notifyTimestampUpdate(newPosition);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mUiUpdateExecutor = Executors.newFixedThreadPool(1);
        mMediaManagementExecutor = Executors.newFixedThreadPool(1);

        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mPlaybackWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mNotificationHelper = new NotificationHelper(this);
        mRemoteControlClient = new RemoteControlClientHelper();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        setAudioListener();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        hasNotification = false;
        mRepeatMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, REPEAT_NONE);
        mShuffleMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, SHUFFLE_NONE);
        int position = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, 0);

        reloadPlaylist();

        if (mPlaylist != null && mPlaylist.length > 0) {
            try {
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
        doUpdateRemoteClient();
        doUpdateNotification();

        mCommandbroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                doManageCommandIntent(intent);
            }
        };

        final IntentFilter intentFilter = new IntentFilter(ACTION_NOTIFICATION_COMMAND);
        intentFilter.addAction(ACTION_CLIENT_COMMAND);

        registerReceiver(mCommandbroadcastReceiver, intentFilter);



        mHeadsetBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (sharedPreferences.getBoolean(PlayerApplication.context.getString(R.string.preference_key_plug_auto_play), true)) {
                    switch (intent.getIntExtra("state", -1)) {
                        case 0:
                            MusicConnector.doPauseActionReceiverIntent();
                            break;
                        case 1:
                            MusicConnector.doPlayActionReceiverIntent();
                            break;
                    }
                }
            }
        };

        final IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mHeadsetBroadcastReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            doManageCommandIntent(intent);
        }

        doUpdateWidgets();

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mCommandbroadcastReceiver);
        unregisterReceiver(mHeadsetBroadcastReceiver);

        stopForeground(true);
        hasNotification = false;

        if (mUiUpdateExecutor != null) {
            mUiUpdateExecutor.shutdown();
        }

        if (mMediaManagementExecutor != null) {
            mMediaManagementExecutor.shutdown();
        }

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

            // should never happen..
            if (!mPlaylist[mPlaylistIndex].isLoaded()) {
                mPlaylist[mPlaylistIndex].load();
            }
            mMediaManagementExecutor.submit(mediaPreloaderRunnable);

            if (!player.playerIsPlaying()) {
                player.playerPlay();
                mPlaybackWakeLock.acquire();
            }

            notifyPlay();
        }

        @Override
        public void pause(boolean keepNotification) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            if (player.playerIsPlaying()) {
                player.playerPause(true);
                mPlaybackWakeLock.release();
            }

            notifyPause(keepNotification);
        }

        @Override
        public void stop() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Player player = mediaManager.getPlayer();

            player.playerStop();

            notifyStop();
            if (mPlaybackWakeLock.isHeld()) {
                mPlaybackWakeLock.release();
            }
        }

        @Override
        public boolean next() throws RemoteException {
            boolean looped = false;

            seekPreviousTrackRunnable.index = mPlaylistIndex;
            mMediaManagementExecutor.submit(seekPreviousTrackRunnable);

            if (mPlaylist != null && mPlaylist.length > 0) {
                looped = doMoveToNextPosition();
                notifySetQueuePosition();
            }

            if (mPlaylist != null) {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
                final AbstractMediaManager.Player player = mediaManager.getPlayer();
                player.playerSetContent(mPlaylist[mPlaylistIndex]);
            }

            return looped;
        }

        class SeekPreviousTrackRunnable implements Runnable {

            public int index;

            @Override
            public void run() {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
                final AbstractMediaManager.Player player = mediaManager.getPlayer();

                player.playerSeek(mPlaylist[index], 0);
            }
        }

        SeekPreviousTrackRunnable seekPreviousTrackRunnable = new SeekPreviousTrackRunnable() {

        };

        @Override
        public boolean prev() throws RemoteException {
            boolean looped = false;

            if (mPlaylist != null && mPlaylist.length > 0) {
                looped = doMoveToPrevPosition();
                notifySetQueuePosition();
            }

            if (mPlaylist != null) {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
                final AbstractMediaManager.Player player = mediaManager.getPlayer();

                player.playerSetContent(mPlaylist[mPlaylistIndex]);
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

            player.playerSeek(mPlaylist[mPlaylistIndex], position);
            notifyTimestampUpdate(position);
        }

        @Override
        public int getShuffleMode() throws RemoteException {
            return mShuffleMode;
        }

        @Override
        public void setShuffleMode(int mode) throws RemoteException {
            mShuffleMode = mode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (getRepeatMode() == REPEAT_CURRENT) {
                    setRepeatMode(REPEAT_ALL);
                }
            }

            notifyShuffleChange();
        }

        @Override
        public int getRepeatMode() throws RemoteException {
            return mRepeatMode;
        }

        @Override
        public void setRepeatMode(int mode) throws RemoteException {
            mRepeatMode = mode;
            if (mRepeatMode == REPEAT_CURRENT) {
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

            reloadPlaylist();

            if (mPlaylistIndex >= mPlaylist.length) {
                mPlaylistIndex = mPlaylist.length - 1;
            }

            if (mPlaylist.length == 1) {
                play();
            }

            notifyQueueChanged();
            mMediaManagementExecutor.submit(runnableRefreshSongData);
        }

        @Override
        public void queueMove(int indexFrom, int indexTo) throws RemoteException {
            if (indexFrom == indexTo) {
                return;
            }

            if (indexFrom < mPlaylistIndex && indexTo >= mPlaylistIndex) {
                mPlaylistIndex--;
            }
            else if (indexFrom > mPlaylistIndex && indexTo <= mPlaylistIndex) {
                mPlaylistIndex++;
            }
            else if (indexFrom == mPlaylistIndex) {
                mPlaylistIndex = indexTo;
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();
            provider.playlistMove(null, indexFrom, indexTo);

            reloadPlaylist();

            if (mPlaylistIndex >= mPlaylist.length) {
                mPlaylistIndex = mPlaylist.length - 1;
            }
        }

        @Override
        public void queueRemove(int entry) throws RemoteException {
            if (entry < mPlaylistIndex) {
                mPlaylistIndex--;
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();
            provider.playlistRemove(null, entry);

            boolean wasPlaying = isPlaying();

            if (mPlaylistIndex == entry) {
                wasPlaying = false;
                stop();
            }
            else {
                pause(true);
            }

            reloadPlaylist();

            if (mPlaylist.length != 0) {
                if (mPlaylistIndex <= mPlaylist.length) {
                    mPlaylistIndex = mPlaylist.length - 1;
                }

                if (wasPlaying) {
                    play();
                }
            }

            notifySetQueuePosition();
            notifyQueueChanged();
        }

        @Override
        public void queueClear() throws RemoteException {
            if (isPlaying()) {
                stop();
            }

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();

            provider.playlistClear(null);
            mPlaylistIndex = 0;
            reloadPlaylist();

            notifyQueueChanged();
        }

        @Override
        public void queueReload() throws RemoteException {
            boolean wasPlaying = isPlaying();

            if (wasPlaying) {
                pause(true);
            }

            String uri = null;
            if (mPlaylist.length > mPlaylistIndex) {
                uri = mPlaylist[mPlaylistIndex].getUri();
            }

            reloadPlaylist();

            if (mPlaylistIndex >= mPlaylist.length) {
                mPlaylistIndex = 0;
            }

            if (mPlaylist != null && mPlaylistIndex < mPlaylist.length) {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
                final AbstractMediaManager.Player player = mediaManager.getPlayer();
                player.playerSetContent(mPlaylist[mPlaylistIndex]);
            }

            if (mPlaylist.length > mPlaylistIndex) {
                if (mPlaylist[mPlaylistIndex].getUri().equals(uri)) {
                    if (wasPlaying) {
                        play();
                    }
                }
            }

            notifyQueueChanged();
            mMediaManagementExecutor.submit(runnableRefreshSongData);
        }


        @Override
        public void queueSetPosition(int position) throws RemoteException {
            if (mPlaylist != null && mPlaylist.length > 0) {
                LogUtils.LOGD(TAG, "moving to position " + position);

                if (mShuffleMode == SHUFFLE_NONE) {
                    if (mPlaylistIndex != position) {
                        mPlaylistIndex = position;
                        if (mPlaylistIndex >= mPlaylist.length) {
                            mPlaylistIndex = 0;
                        }
                    }
                } else if (mShuffleMode == SHUFFLE_AUTO) {
                    int currentTrackIndex = mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
                    if (currentTrackIndex != position) {
                        int indexOfPosition = mShuffledPlaylistIndexList.indexOf(position);
                        mShuffledPlaylistIndexList.set(indexOfPosition, currentTrackIndex);
                        mShuffledPlaylistIndexList.set(mShuffledPlaylistIndex, position);

                        mPlaylistIndex = position;
                    }
                }

                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
                final AbstractMediaManager.Player player = mediaManager.getPlayer();

                player.playerSetContent(mPlaylist[mPlaylistIndex]);

                notifySetQueuePosition();
            }
            else if (position == 0) {
                mPlaylistIndex = 0;
            }
        }

        @Override
        public int queueGetPosition() throws RemoteException {
            if (mPlaylist == null || mPlaylist.length == 0) {
                return -1;
            }

            if (mShuffleMode == SHUFFLE_NONE) {
                return mPlaylistIndex;
            }
            else if (mShuffleMode == SHUFFLE_AUTO) {
                return mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
            }

            return 0;
        }

        @Override
        public int queueGetSize() throws RemoteException {
            return mPlaylist.length;
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
            mUiUpdateExecutor.submit(runnablePlay);

            lockNotify();
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
            Runnable runnablePause = keepNotification ? runnablePauseKeepingNotification : runnablePauseNotKeepingNotification;
            mUiUpdateExecutor.submit(runnablePause);

            lockNotify();

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
            mUiUpdateExecutor.submit(runnableStop);

            lockNotify();
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
            doUpdateWidgets();

            lockNotify();
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
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, mPlaylistIndex);
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
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, mShuffleMode);
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
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, mRepeatMode);
            edit.apply();
            unlockNotify();
        }

        private void notifySetQueuePosition() {
            mUiUpdateExecutor.submit(runnableRefreshSongData);

            lockNotify();
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
            edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, mPlaylistIndex);
            edit.apply();
            unlockNotify();
        }
    }

    @TargetApi(14)
    private void setAudioListener() {
        if (PlayerApplication.hasICS()) {
            mAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        mRemoteControlClient.release();
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

            if (mPlaylist != null && mPlaylist.length > 0) {
                final AbstractMediaManager.Media media = mPlaylist[mPlaylistIndex];

                AbstractAppWidget.setHasPlaylist(true);
                AbstractAppWidget.setPlaying(isPlaying);
                AbstractAppWidget.setMetadata(media.name, media.artist, media.album, currentArt);

                mWidgetLarge.applyUpdate(this);
                mWidgetMedium.applyUpdate(this);

            }
            else {
                AbstractAppWidget.setHasPlaylist(false);
                AbstractAppWidget.setPlaying(isPlaying);
                AbstractAppWidget.setMetadata(null, null, null, null);

                mWidgetLarge.applyUpdate(this);
                mWidgetMedium.applyUpdate(this);
            }
        }
        catch (final Exception exception) {
            LogUtils.LOGException(TAG, "doUpdateWidgets", 0, exception);
        }
    }

    protected void doUpdateRemoteClient() {
        if (mPlaylist.length > 0) {
            final AbstractMediaManager.Media media = mPlaylist[mPlaylistIndex];
            mRemoteControlClient.updateMetadata(currentArt, media.name, media.artist, media.album, media.duration);
        }
        else {
            mRemoteControlClient.updateMetadata(currentArt, null, null, null, 0);
        }
    }

    protected void doUpdateNotification() {
        if (mPlaylist.length > 0) {
            final AbstractMediaManager.Media media = mPlaylist[mPlaylistIndex];
            mNotificationHelper.buildNotification(media.album, media.artist, media.name, currentArt);
        }
        else {
            mNotificationHelper.buildNotification(null, null, null, null);
        }

        try {
            if (playerServiceImpl.isPlaying()) {
                startForeground(PlayerApplication.NOTIFICATION_PLAY_ID, mNotificationHelper.getNotification());
            }
        }
        catch (final Exception exception) {
            LogUtils.LOGException(TAG, "doUpdateNotification", 0, exception);
        }
    }

    protected void doManageCommandIntent(final Intent intent) {
        if (intent.hasExtra(COMMAND_KEY)) {
            final String source = intent.getAction();
            final String action = intent.getStringExtra(PlayerService.COMMAND_KEY);

            boolean isNotificationControl = source.equals(PlayerService.ACTION_NOTIFICATION_COMMAND);
            boolean isWidgetControl = source.equals(PlayerService.ACTION_APPWIDGET_COMMAND);
            boolean isClientControl = source.equals(PlayerService.ACTION_CLIENT_COMMAND);
            boolean isRemoteControl = isNotificationControl || isWidgetControl || isClientControl;

            if (action != null && isRemoteControl) {
                try {
                    if (action.equals(PlayerService.ACTION_PREVIOUS)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(true);
                            playerServiceImpl.setPosition(0);
                            playerServiceImpl.prev();
                            playerServiceImpl.play();
                        }
                        else {
                            playerServiceImpl.prev();
                        }
                    }
                    else if (action.equals(PlayerService.ACTION_NEXT)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(true);
                            playerServiceImpl.setPosition(0);
                            playerServiceImpl.next();
                            playerServiceImpl.play();
                        }
                        else {
                            playerServiceImpl.next();
                        }
                    }
                    else if (action.equals(PlayerService.ACTION_STOP)) {
                        playerServiceImpl.stop();
                    }
                    else if (action.equals(PlayerService.ACTION_TOGGLEPAUSE)) {
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(isNotificationControl);
                        } else {
                            if (mPlaylist != null && mPlaylist.length > 0) {
                                playerServiceImpl.play();
                            }
                        }
                    }
                    else if (action.equals(PlayerService.ACTION_PLAY)) {
                        if (!playerServiceImpl.isPlaying()) {
                            if (mPlaylist != null && mPlaylist.length > 0) {
                                playerServiceImpl.play();
                            }
                        }
                    }
                    else if (action.equals(PlayerService.ACTION_PAUSE)) {
                        LogUtils.LOGD(TAG, "pause");
                        if (playerServiceImpl.isPlaying()) {
                            playerServiceImpl.pause(isNotificationControl);
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
        if (mShuffleMode == SHUFFLE_NONE) {
            mPlaylistIndex++;
            if (mPlaylistIndex >= mPlaylist.length) {
                mPlaylistIndex = 0;
                looped = true;
            }
        }
        else if (mShuffleMode == SHUFFLE_AUTO) {
            mShuffledPlaylistIndex++;
            if (mShuffledPlaylistIndex >= mShuffledPlaylistIndexList.size()) {
                mShuffledPlaylistIndex = 0;
                looped = true;
            }


            mPlaylistIndex = mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
        }
        return looped;
    }

    protected boolean doMoveToPrevPosition() {
        boolean looped = false;
        if (mShuffleMode == SHUFFLE_NONE) {
            mPlaylistIndex--;
            if (mPlaylistIndex < 0) {
                mPlaylistIndex = mPlaylist.length - 1;
                looped = true;
            }
        }
        else if (mShuffleMode == SHUFFLE_AUTO) {
            mShuffledPlaylistIndex--;
            if (mShuffledPlaylistIndex < 0) {
                mShuffledPlaylistIndex = mShuffledPlaylistIndexList.size() - 1;
                looped = true;
            }

            mPlaylistIndex = mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
        }
        return looped;
    }

    protected void reloadPlaylist() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        mPlaylist = provider.getCurrentPlaylist(mediaManager.getPlayer());

        int playlistLength = mPlaylist != null ? mPlaylist.length : 0;

        mShuffledPlaylistIndexList = new ArrayList<Integer>();
        for (int playlistIndex = 0 ; playlistIndex < playlistLength ; playlistIndex++) {
            mShuffledPlaylistIndexList.add(playlistIndex);
        }

        Collections.shuffle(mShuffledPlaylistIndexList);
        mShuffledPlaylistIndex = 0;

        doUpdateWidgets();
    }

    protected void lockNotify() {
        notifyMutex.lock();
    }

    protected void unlockNotify() {
        notifyMutex.unlock();
    }


    private final ImageLoadingListener artImageLoaderListener = new ImageLoadingListener() {

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
            String currentArtUri = null;
            if (mPlaylistIndex < mPlaylist.length) {
                currentArtUri = mPlaylist[mPlaylistIndex].artUri;
            }

            if (imageUri.equals(currentArtUri)) {
                if (loadedImage != null && loadedImage.isRecycled()) {
                    PlayerApplication.normalImageLoader.loadImage(currentArtUri, (DisplayImageOptions) null, artImageLoaderListener);
                }
                else {
                    currentArt = loadedImage;
                    doUpdateNotification();
                    doUpdateRemoteClient();
                    doUpdateWidgets();
                }
            }
        }

        @Override
        public void onLoadingCancelled(String imageUri, View view) {
            currentArt = null;
            doUpdateNotification();
            doUpdateRemoteClient();
            doUpdateWidgets();
        }
    };

    private final Runnable runnablePlay = new Runnable() {

        @Override
        public void run() {
            /*
                System ui update
             */
            int audioFocus = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (PlayerApplication.hasICS()) {
                audioFocus = mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            mRemoteControlClient.register(PlayerService.this, mAudioManager);
            if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (hasNotification) {
                    mNotificationHelper.goToIdleState(true);
                } else {
                    startForeground(PlayerApplication.NOTIFICATION_PLAY_ID, mNotificationHelper.getNotification());
                    hasNotification = true;
                }
                mRemoteControlClient.updateState(true);
            }

            doUpdateWidgets();
            doUpdateRemoteClient();
            doUpdateNotification();
        }
    };

    private final Runnable runnablePauseKeepingNotification = new Runnable() {
        @Override
        public void run() {
            mRemoteControlClient.updateState(false);
            mNotificationHelper.goToIdleState(false);

            doUpdateWidgets();
        }
    };

    private final Runnable runnablePauseNotKeepingNotification = new Runnable() {
        @Override
        public void run() {
            mRemoteControlClient.updateState(false);

            stopForeground(true);
            hasNotification = false;

            if (PlayerApplication.hasICS()) {
                mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            }

            doUpdateWidgets();
        }
    };

    private final Runnable runnableStop = new Runnable() {

        @Override
        public void run() {
            stopForeground(true);
            hasNotification = false;
            mRemoteControlClient.stop();
            mRemoteControlClient.release();

            doUpdateWidgets();

            if (PlayerApplication.hasICS()) {
                mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            }
        }
    };

    private final Runnable runnableRefreshSongData = new Runnable() {
        @Override
        public void run() {
            try {
                // Avoid cpu stress that can break gapless :s
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            }
            catch (final Exception interruptException) {
                LogUtils.LOGException(TAG, "refreshData", 0, interruptException);
            }

            String currentArtUri = null;
            if (mPlaylist != null) {
                if (mPlaylistIndex < mPlaylist.length) {
                    currentArtUri = mPlaylist[mPlaylistIndex].artUri;
                }

                PlayerApplication.normalImageLoader.loadImage(currentArtUri, (DisplayImageOptions) null, artImageLoaderListener);
            }
        }
    };

    class MediaLoaderRunnable implements Runnable {

        public AbstractMediaManager.Media track;

        public MediaLoaderRunnable(AbstractMediaManager.Media track) {
            this.track = track;
        }

        @Override
        public void run() {
            track.load();
        }
    }

    private final MediaPreloaderRunnable mediaPreloaderRunnable = new MediaPreloaderRunnable();

    class MediaPreloaderRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "loader in action !");
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            int nextPlaylistIndex = mPlaylistIndex;

            if (mShuffleMode == SHUFFLE_NONE) {
                nextPlaylistIndex++;
                if (nextPlaylistIndex >= mPlaylist.length) {
                    nextPlaylistIndex = 0;
                }
            }
            else if (mShuffleMode == SHUFFLE_AUTO) {
                int nextPlaylistOrderIndex = mShuffledPlaylistIndex + 1;
                if (nextPlaylistOrderIndex >= mShuffledPlaylistIndexList.size()) {
                    nextPlaylistOrderIndex = 0;
                }

                nextPlaylistIndex = mShuffledPlaylistIndexList.get(nextPlaylistOrderIndex);
            }

            for (int mediaIndex = 0 ; mediaIndex < mPlaylist.length ; mediaIndex++) {
                if (mediaIndex == nextPlaylistIndex) {
                    mPlaylist[mediaIndex].load();
                }
                else if (mediaIndex != mPlaylistIndex) {
                    mPlaylist[mediaIndex].unload();
                }
            }

            mMediaManagementExecutor.submit(new MediaLoaderRunnable(mPlaylist[nextPlaylistIndex]));
        }
    }
}
