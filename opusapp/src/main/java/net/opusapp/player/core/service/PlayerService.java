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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

import net.opusapp.player.R;
import net.opusapp.player.core.RemoteControlClientHelper;
import net.opusapp.player.core.service.event.MediaChangedEvent;
import net.opusapp.player.core.service.event.MediaCoverChangedEvent;
import net.opusapp.player.core.service.event.PlayStateChangedEvent;
import net.opusapp.player.core.service.event.PlaylistChangedEvent;
import net.opusapp.player.core.service.event.RepeatModeChangedEvent;
import net.opusapp.player.core.service.event.ShuffleModeChangedEvent;
import net.opusapp.player.core.service.event.TimestampChangedEvent;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.event.PlaybackTerminatedEvent;
import net.opusapp.player.core.service.providers.event.PlaybackTimestampChangedEvent;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.widgets.AbstractAppWidget;
import net.opusapp.player.ui.widgets.AppWidget4x1;
import net.opusapp.player.ui.widgets.AppWidget4x2;
import net.opusapp.player.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PlayerService extends Service {

	private final static String TAG = PlayerService.class.getSimpleName();




    // Empty playlist
    public static final MediaManager.Media[] EMPTY_PLAYLIST = new MediaManager.Media[0];


    // Command
    public static final String COMMAND_KEY = "net.opusapp.player.core.service.COMMAND_KEY";

    public static final String COMMAND_SOURCE_APPWIDGET = "net.opusapp.player.core.service.COMMAND_SOURCE_APPWIDGET";

    public static final String COMMAND_SOURCE_NOTIFICATION = "net.opusapp.player.core.service.COMMAND_SOURCE_NOTIFICATION";

    public static final String COMMAND_SOURCE_TELEPHONY = "net.opusapp.player.core.service.COMMAND_SOURCE_TELEPHONY";

    public static final String COMMAND_SOURCE_CLIENT_APP = "net.opusapp.player.core.service.COMMAND_SOURCE_CLIENT_APP";



    public static final String ACTION_REFRESH_WIDGETS = "net.opusapp.player.core.service.ACTION_REFRESH_WIDGETS";

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


    // Playstate
    public static final int PLAYSTATE_PLAYING = 0;

    public static final int PLAYSTATE_PAUSED = 1;

    public static final int PLAYSTATE_STOPPED = 2;



    // Notification
    private static final int NOTIFICATION_ID = 1;

    private Notification mNotification = null;

    private NotificationManager mNotificationManager;

    private RemoteViews mCollapsedView;

    private RemoteViews mExpandedView;

    private int mPlayDrawable;

    private int mPauseDrawable;



    // Service management.
    public boolean mIsForeground;

    private PlayerBinder mBinder;

    private WakeLock mPlaybackWakeLock;

    private RemoteControlClientHelper mRemoteControlClient;

    private AudioManager mAudioManager;

    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener;

    private BroadcastReceiver mCommandbroadcastReceiver;

    private BroadcastReceiver mHeadsetBroadcastReceiver;

    private final AppWidget4x1 mWidgetMedium = AppWidget4x1.getInstance();

    private final AppWidget4x2 mWidgetLarge = AppWidget4x2.getInstance();

    private ExecutorService mUiUpdateExecutor;

    private ExecutorService mMediaManagementExecutor;



    private int mRepeatMode = REPEAT_NONE;

    private int mShuffleMode = SHUFFLE_NONE;

    private int mPlayState = PLAYSTATE_STOPPED;



    private MediaManager.Media[] mPlaylist = EMPTY_PLAYLIST;

    private List<Integer> mShuffledPlaylistIndexList;

    private int mShuffledPlaylistIndex;

    private boolean mPausedByTelephony = false;

    private int mPlaylistIndex;

    private Date mAutostopTimestamp = null;



    // Current song informations
    private long mTimeStamp;

    private int mLoadingTry;

    private TimestampChangedEvent mTimestampChangedEvent = new TimestampChangedEvent();

    private MediaChangedEvent mMedia = new MediaChangedEvent(null, null, null, 0, -1);

    private String mMediaCoverUri = null;

    private Bitmap mMediaCover = null;


    @SuppressWarnings("unused")
    @Subscribe
    public void playbackTerminated(PlaybackTerminatedEvent playbackTerminatedEvent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LogUtils.LOGD(TAG, "completed track");
                switch (mRepeatMode) {
                    case REPEAT_ALL:
                        next();
                        play();
                        break;
                    case REPEAT_CURRENT:
                        setPosition(0);
                        play();
                        break;
                    case REPEAT_NONE:
                        if (!next()) {
                            play();
                        }
                        else { /* cannot play anymore */
                            stop();
                            mTimeStamp = 0;
                            mTimestampChangedEvent.setTimestamp(0);
                            ProviderEventBus.getInstance().post(produceTimestampChangedEvent());
                        }

                        break;
                }
            }
        }).start();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void playbackTimestampChanged(PlaybackTimestampChangedEvent playbackTimestampChangedEvent) {
        /* Playing, request for timestamp update */
        mTimeStamp = playbackTimestampChangedEvent.getTimeStamp();

        mTimestampChangedEvent.setTimestamp(mTimeStamp);
        ProviderEventBus.getInstance().post(produceTimestampChangedEvent());

        if (getAutostopTimestamp() == 0) {
            LogUtils.LOGD(TAG, "autostop timestamp reached : " + getAutostopTimestamp());
            setAutoStopTimestamp(-1);
            pause(true);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mBinder = new PlayerBinder();

        mUiUpdateExecutor = Executors.newFixedThreadPool(1);
        mMediaManagementExecutor = Executors.newFixedThreadPool(1);

        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mPlaybackWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        // Notification system (external control)
        mNotificationManager = (NotificationManager) PlayerApplication.context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (PlayerApplication.hasLollipop()) {
            mPlayDrawable = R.drawable.ic_play_arrow_grey600_48dp;
            mPauseDrawable = R.drawable.ic_pause_grey600_48dp;
        }
        else {
            mPlayDrawable = R.drawable.ic_play_arrow_white_48dp;
            mPauseDrawable = R.drawable.ic_pause_white_48dp;
        }

        // Remote client (external control)
        mRemoteControlClient = new RemoteControlClientHelper();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (PlayerApplication.hasICS()) {
            mAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        mRemoteControlClient.release();
                        pause(false);
                    }
                }
            };
        }

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        mIsForeground = false;
        mRepeatMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, REPEAT_NONE);
        mShuffleMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, SHUFFLE_NONE);
        int position = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, 0);

        loadPlaylist(true);

        if (mPlaylist.length > 0) {
            queueSetPosition(position);
        }

        mCommandbroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                doManageCommandIntent(intent);
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(COMMAND_SOURCE_TELEPHONY);
        intentFilter.addAction(COMMAND_SOURCE_CLIENT_APP);
        LocalBroadcastManager.getInstance(PlayerApplication.context).registerReceiver(mCommandbroadcastReceiver, intentFilter);


        mHeadsetBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (sharedPreferences.getBoolean(PlayerApplication.context.getString(R.string.preference_key_plug_auto_play), true)) {
                    switch (intent.getIntExtra("state", -1)) {
                        case 0:
                            if (isPlaying()) {
                                pause(false);
                            }
                            break;
                        case 1:
                            if (mPlaylistIndex < mPlaylist.length) {
                                play();
                            }
                            break;
                    }
                }
            }
        };

        final IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mHeadsetBroadcastReceiver, filter);

        LogUtils.LOGI(TAG, "registering to bus " + ProviderEventBus.getInstance());
        ProviderEventBus.getInstance().register(this);
        PlayerEventBus.getInstance().register(this);
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
        PlayerEventBus.getInstance().unregister(this);
        ProviderEventBus.getInstance().unregister(this);

        unregisterReceiver(mCommandbroadcastReceiver);
        unregisterReceiver(mHeadsetBroadcastReceiver);

        mIsForeground = false;
        stopForeground(true);

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
        return mBinder;
    }



    public class PlayerBinder extends Binder {

        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    protected void updateWidgets() {
        AbstractAppWidget.setHasPlaylist(hasPlaylist());
        AbstractAppWidget.setPlaying(isPlaying());

        AbstractAppWidget.setMetadata(mMedia.getTitle(), mMedia.getArtist(), mMedia.getAlbum(), mMediaCover);

        mWidgetLarge.applyUpdate(this);
        mWidgetMedium.applyUpdate(this);
    }

    @SuppressWarnings("NewApi")
    //updateNotification(mMedia.getTitle(), mMedia.getArtist(), mMedia.getAlbum(), onlyPlaystate, mMediaCover);
    public void updateNotification(final String trackName, final String artistName, final String albumName, final Bitmap albumArt) {
        final Intent intent = new Intent(PlayerApplication.context, LibraryMainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(PlayerApplication.context, 0, intent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(PlayerApplication.context)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(albumArt)
                .setContentTitle(albumName)
                .setContentText(String.format(PlayerApplication.context.getString(R.string.notification_fallback_info_format), trackName, artistName))
                .setContentIntent(pendingIntent);

        if (PlayerApplication.hasHoneycomb()) {
            mCollapsedView = new RemoteViews(PlayerApplication.context.getPackageName(), R.layout.notification_template_base);
            mNotification = builder
                    .setContent(mCollapsedView)
                    .build();

            // Actions
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
            mCollapsedView.setOnClickPendingIntent(R.id.notification_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

            mCollapsedView.setImageViewResource(R.id.notification_base_play, mPauseDrawable);

            // Media informations
            mCollapsedView.setTextViewText(R.id.notification_base_line_one, trackName);
            mCollapsedView.setTextViewText(R.id.notification_base_line_two, artistName);

            if (albumArt != null) {
                mCollapsedView.setImageViewBitmap(R.id.notification_base_image, albumArt);
            }
            else {
                mCollapsedView.setImageViewResource(R.id.notification_base_image, R.drawable.no_art_normal);
            }
        }
        else {
            mNotification = builder.build();
            mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
            mNotification.icon = R.drawable.ic_notification;
            mNotification.contentIntent = pendingIntent;
        }

        if (PlayerApplication.hasJellyBean()) {
            mExpandedView = new RemoteViews(PlayerApplication.context.getPackageName(), R.layout.notification_template_expanded_base);
            mNotification.bigContentView = mExpandedView;

            // Actions
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play, PlayerService.NOTIFICATION_PAUSE_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next, PlayerService.NOTIFICATION_NEXT_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous, PlayerService.NOTIFICATION_PREV_INTENT);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse, PlayerService.NOTIFICATION_STOP_INTENT);

            mExpandedView.setImageViewResource(R.id.notification_expanded_base_play, mPauseDrawable);

            // Media informations
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_one, trackName);
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_two, albumName);
            mExpandedView.setTextViewText(R.id.notification_expanded_base_line_three, artistName);

            if (albumArt != null) {
                mExpandedView.setImageViewBitmap(R.id.notification_expanded_base_image, albumArt);
            }
            else {
                mExpandedView.setImageViewResource(R.id.notification_expanded_base_image, R.drawable.no_art_normal);
            }
        }

        if (mIsForeground) {
            if (mCollapsedView != null) {
                mCollapsedView.setImageViewResource(R.id.notification_base_play, isPlaying() ? mPauseDrawable : mPlayDrawable);
            }

            if (mExpandedView != null) {
                mExpandedView.setImageViewResource(R.id.notification_expanded_base_play, isPlaying() ? mPauseDrawable : mPlayDrawable);
            }

            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        }
    }

    RequestListener<String, Bitmap> mRequestListener = new RequestListener<String, Bitmap>() {
        @Override
        public boolean onException(Exception e, String model, Target<Bitmap> target, boolean isFirstResource) {
            LogUtils.LOGException(TAG, "onException", 0, e);

            return false;
        }

        @Override
        public boolean onResourceReady(Bitmap resource, String model, Target<Bitmap> target, boolean isFromMemoryCache, boolean isFirstResource) {
            LogUtils.LOGW(TAG, "onResourceReady");
            if (resource.isRecycled()) {
                loadCover();
            }
            else {
                mMediaCover = resource;
                updateExternalControlers(false, true);
            }

            return true;
        }
    };

    protected void loadCover() {
        if (mLoadingTry > 3) {
            LogUtils.LOGW(TAG, "Exceeded loading tries");
            return;
        }

        mLoadingTry++;

        final Handler mainHandler = new Handler(PlayerApplication.context.getMainLooper());
        final Runnable loadBitmap = new Runnable() {

            @Override
            public void run() {
                Glide.with(PlayerApplication.context)
                        .load(mMediaCoverUri)
                        .asBitmap()
                        .centerCrop()
                        .listener(mRequestListener)
                        .into(500, 500);
            }
        };

        mainHandler.post(loadBitmap);
    }

    protected synchronized void updateExternalControlers(boolean onlyPlaystate, boolean coverIsLoaded) {
        LogUtils.LOGW(TAG, "updateExternalControlers with {onlyPlaystate=" + onlyPlaystate + ", coverIsLoaded=" + coverIsLoaded + "}");

        mLoadingTry = 0;

        if (mPlaylist.length > 0) {
            if (!onlyPlaystate) {
                if (!coverIsLoaded) {
                    LogUtils.LOGD(TAG, "updateExternalControlers -> querying for cover loading with mMediaCoverUri = " + mMediaCoverUri);

                    if (mMediaCoverUri != null) {
                        LogUtils.LOGD(TAG, "updateExternalControlers: triggering cover loading");
                        loadCover();
                    }
                }
            }
        }

        // Updating widget
        updateWidgets();

        // Updating remote client
        mRemoteControlClient.updateMetadata(mMedia.getTitle(), mMedia.getArtist(), mMedia.getAlbum(), mMedia.getDuration(), mMediaCover);

        // Updating notification
        updateNotification(mMedia.getTitle(), mMedia.getArtist(), mMedia.getAlbum(), mMediaCover);

        notifyCoverLoaded();
    }

    protected void doManageCommandIntent(final Intent intent) {
        if (intent.hasExtra(COMMAND_KEY)) {
            final String source = intent.getAction();
            final String action = intent.getStringExtra(PlayerService.COMMAND_KEY);

            boolean isNotificationControl = source.equals(PlayerService.COMMAND_SOURCE_NOTIFICATION);
            boolean isWidgetControl = source.equals(PlayerService.COMMAND_SOURCE_APPWIDGET);
            boolean isTelephonyControl = source.equals(COMMAND_SOURCE_TELEPHONY);
            boolean isClientControl = source.equals(PlayerService.COMMAND_SOURCE_CLIENT_APP);
            boolean isRemoteControl = isNotificationControl || isWidgetControl || isClientControl;

            if (action != null) {
                if (isRemoteControl) {
                    switch (action) {
                    case ACTION_PREVIOUS:
                        if (mTimeStamp > 4000) {
                            if (isPlaying()) {
                                pause(true);
                                setPosition(0);
                                play();
                            }
                            else {
                                setPosition(0);
                            }
                        }
                        else {
                            if (isPlaying()) {
                                pause(true);
                                setPosition(0);
                                prev();
                                play();
                            } else {
                                prev();
                            }
                        }
                        break;
                    case ACTION_NEXT:
                        if (isPlaying()) {
                            pause(true);
                            setPosition(0);
                            next();
                            play();
                        } else {
                            next();
                        }
                        break;
                    case ACTION_STOP:
                        stop();
                        break;
                    case ACTION_TOGGLEPAUSE:
                        if (isPlaying()) {
                            pause(isNotificationControl);
                        } else {
                            if (mPlaylist.length > 0) {
                                play();
                            }
                        }
                        break;
                    case ACTION_PLAY:
                        if (!isPlaying()) {
                            if (mPlaylist.length > 0) {
                                play();
                            }
                        }
                        break;
                    case ACTION_PAUSE:
                        LogUtils.LOGD(TAG, "pause");
                        if (isPlaying()) {
                            pause(isNotificationControl);
                        }
                        break;
                    case ACTION_REFRESH_WIDGETS:
                        updateWidgets();
                        break;
                    }
                }
                else if (isTelephonyControl) {
                    switch (action) {
                        case ACTION_PLAY:
                            LogUtils.LOGD(TAG, "telephony : querying ACTION_PLAY");
                            if (pausedByTelephopny()) {
                                setPausedByTelephony(false);
                                LogUtils.LOGD(TAG, "telephony : ACTION_PLAY");

                                if (!isPlaying()) {
                                    if (mPlaylist.length > 0) {
                                        play();
                                    }
                                }
                            }
                        break;
                        case ACTION_PAUSE:
                            LogUtils.LOGD(TAG, "telephony : querying ACTION_PAUSE");
                            if (isPlaying()) {
                                LogUtils.LOGD(TAG, "telephony : ACTION_PAUSE");
                                setPausedByTelephony(true);
                                pause(false);
                            }
                        break;
                    }
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

    protected void loadPlaylist(boolean notify) {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        mPlaylist = provider.getCurrentPlaylist(mediaManager.getPlayer());

        mShuffledPlaylistIndexList = new ArrayList<>();
        for (int playlistIndex = 0 ; playlistIndex < mPlaylist.length ; playlistIndex++) {
            mShuffledPlaylistIndexList.add(playlistIndex);
        }

        Collections.shuffle(mShuffledPlaylistIndexList);
        mShuffledPlaylistIndex = 0;

        if (notify) {
            notifyQueueChanged();

        }
    }

    protected boolean pausedByTelephopny() {
        return mPausedByTelephony;
    }

    protected void setPausedByTelephony(boolean paused) {
        mPausedByTelephony = paused;
    }

    private final Runnable runnablePlay = new Runnable() {

        @Override
        public void run() {
            // System UI update
            int audioFocus = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (PlayerApplication.hasICS()) {
                audioFocus = mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            mRemoteControlClient.register(PlayerService.this, mAudioManager);
            if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (!mIsForeground) {
                    LogUtils.LOGW(TAG, "runnablePlay: showing notification");
                    startForeground(NOTIFICATION_ID, mNotification);
                    mIsForeground = true;
                }
                mRemoteControlClient.updateState(true);
            }

            updateExternalControlers(true, true);
        }
    };

    private final Runnable runnablePauseKeepingNotification = new Runnable() {
        @Override
        public void run() {
            mRemoteControlClient.updateState(false);
            updateExternalControlers(true, true);
        }
    };

    private final Runnable runnablePauseNotKeepingNotification = new Runnable() {
        @Override
        public void run() {
            mRemoteControlClient.updateState(false);

            mIsForeground = false;
            stopForeground(true);

            if (PlayerApplication.hasICS()) {
                mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            }

            updateExternalControlers(true, true);
        }
    };

    private final Runnable runnableStop = new Runnable() {

        @Override
        public void run() {
            mIsForeground = false;
            stopForeground(true);

            mRemoteControlClient.stop();
            mRemoteControlClient.release();

            updateExternalControlers(true, true);

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

            updateExternalControlers(false, false);
        }
    };

    class MediaLoaderRunnable implements Runnable {

        public MediaManager.Media track;

        public MediaLoaderRunnable(MediaManager.Media track) {
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


    // Public API helpers
    protected boolean hasPlaylist() {
        return mPlaylist.length > 0;
    }



    class SeekPreviousTrackRunnable implements Runnable {

        public int index;

        @Override
        public void run() {
            final MediaManager mediaManager = PlayerApplication.playerMediaManager();
            final MediaManager.Player player = mediaManager.getPlayer();

            player.playerSeek(mPlaylist[index], 0);
        }
    }

    protected  SeekPreviousTrackRunnable mSeekPreviousTrackRunnable = new SeekPreviousTrackRunnable();


    // Public API
    public void play() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

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

    public void pause(boolean keepNotification) {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        if (player.playerIsPlaying()) {
            player.playerPause(true);
            mPlaybackWakeLock.release();
        }

        notifyPause(keepNotification);
    }

    public void stop() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        player.playerStop();
        mTimeStamp = 0;

        notifyStop();
        if (mPlaybackWakeLock.isHeld()) {
            mPlaybackWakeLock.release();
        }
    }

    public boolean next() {
        boolean looped = false;

        mSeekPreviousTrackRunnable.index = mPlaylistIndex;
        mMediaManagementExecutor.submit(mSeekPreviousTrackRunnable);

        if (hasPlaylist()) {
            looped = doMoveToNextPosition();
            notifySetQueuePosition();

            if (mPlaylistIndex < mPlaylist.length) {
                final MediaManager mediaManager = PlayerApplication.playerMediaManager();
                final MediaManager.Player player = mediaManager.getPlayer();
                player.playerSetContent(mPlaylist[mPlaylistIndex]);
            }
        }

        return looped;
    }

    public boolean prev() {
        boolean looped = false;

        if (hasPlaylist() && mPlaylistIndex < mPlaylist.length) {
            looped = doMoveToPrevPosition();
            notifySetQueuePosition();

            if (mPlaylistIndex < mPlaylist.length) {
                final MediaManager mediaManager = PlayerApplication.playerMediaManager();
                final MediaManager.Player player = mediaManager.getPlayer();
                player.playerSetContent(mPlaylist[mPlaylistIndex]);
            }
        }

        return looped;
    }

    public boolean isPlaying() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        return player.playerIsPlaying();
    }

    public long getDuration() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        return player.playerGetDuration();
    }

    public long getPosition() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        return player.playerGetPosition();
    }

    public void setPosition(long position) {
        if (!hasPlaylist()) {
            return;
        }

        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        mTimeStamp = position;
        player.playerSeek(mPlaylist[mPlaylistIndex], position);

        mTimestampChangedEvent.setTimestamp(mTimeStamp);
        ProviderEventBus.getInstance().post(produceTimestampChangedEvent());
    }

    public int getShuffleMode() {
        return mShuffleMode;
    }

    public void setShuffleMode(int mode) {
        mShuffleMode = mode;
        if (mShuffleMode == SHUFFLE_AUTO) {
            if (getRepeatMode() == REPEAT_CURRENT) {
                setRepeatMode(REPEAT_ALL);
            }
        }

        notifyShuffleChange();
    }

    public int getRepeatMode() {
        return mRepeatMode;
    }

    public void setRepeatMode(int mode) {
        mRepeatMode = mode;
        if (mRepeatMode == REPEAT_CURRENT && getShuffleMode() != SHUFFLE_NONE) {
            setShuffleMode(SHUFFLE_NONE);
        }

        notifyRepeatChange();
    }

    public void queueAdd(String media) {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();
        provider.playlistAdd(null, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, media, 0, null);

        loadPlaylist(true);

        if (mPlaylistIndex >= mPlaylist.length) {
            mPlaylistIndex = mPlaylist.length - 1;
        }

        if (mPlaylist.length == 1) {
            play();
        }

        mMediaManagementExecutor.submit(runnableRefreshSongData);
    }

    public void queueMove(int indexFrom, int indexTo) {
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

        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();
        provider.playlistMove(null, indexFrom, indexTo);

        loadPlaylist(true);

        if (mPlaylistIndex >= mPlaylist.length) {
            mPlaylistIndex = mPlaylist.length - 1;
        }
    }

    public void queueRemove(int entry) {
        if (entry < mPlaylistIndex) {
            mPlaylistIndex--;
        }

        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();
        provider.playlistRemove(null, entry);

        boolean wasPlaying = isPlaying();

        if (mPlaylistIndex == entry) {
            wasPlaying = false;
            stop();
        }
        else {
            pause(true);
        }

        loadPlaylist(true);

        if (mPlaylist.length != 0) {
            if (mPlaylistIndex >= mPlaylist.length) {
                mPlaylistIndex = mPlaylist.length - 1;
            }

            if (wasPlaying) {
                play();
            }
        }

        notifySetQueuePosition();
    }

    public void queueClear() {
        if (isPlaying()) {
            stop();
        }

        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        provider.playlistClear(null);
        mPlaylistIndex = 0;
        loadPlaylist(true);

        // Widgets are always displayed, even if not playing so we need to update them if playlist is now empty.
        updateWidgets();
    }

    public void queueReload() {
        final MediaManager mediaManager = PlayerApplication.playerMediaManager();
        final MediaManager.Player player = mediaManager.getPlayer();

        boolean wasPlaying = player.playerIsPlaying();

        if (wasPlaying) {
            player.playerPause(true);
        }

        final String uri = mPlaylistIndex < mPlaylist.length ? mPlaylist[mPlaylistIndex].getUri() : null;

        loadPlaylist(true);

        if (mPlaylistIndex >= mPlaylist.length) {
            mPlaylistIndex = 0;
        }

        if (mPlaylistIndex < mPlaylist.length) {
            if (mPlaylist[mPlaylistIndex].getUri().equals(uri) && wasPlaying) {
                player.playerPause(false);
            }
            else {
                player.playerStop();
                player.playerSetContent(mPlaylist[mPlaylistIndex]);
            }
        }

        mMediaManagementExecutor.submit(runnableRefreshSongData);
    }

    public void queueSetPosition(int position) {
        if (mPlaylist.length > 0) {
            LogUtils.LOGD(TAG, "moving to position " + position);

            switch (mShuffleMode) {
                case SHUFFLE_NONE:
                    if (mPlaylistIndex != position) {
                        mPlaylistIndex = position;
                        if (mPlaylistIndex >= mPlaylist.length) {
                            mPlaylistIndex = 0;
                        }
                    }
                    break;
                case SHUFFLE_AUTO:
                    int currentTrackIndex = mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
                    if (currentTrackIndex != position) {
                        int indexOfPosition = mShuffledPlaylistIndexList.indexOf(position);
                        mShuffledPlaylistIndexList.set(indexOfPosition, currentTrackIndex);
                        mShuffledPlaylistIndexList.set(mShuffledPlaylistIndex, position);

                        mPlaylistIndex = position;
                    }
                    break;
            }

            final MediaManager mediaManager = PlayerApplication.playerMediaManager();
            final MediaManager.Player player = mediaManager.getPlayer();

            player.playerSetContent(mPlaylist[mPlaylistIndex]);

            notifySetQueuePosition();
        }
        else if (position == 0) {
            mPlaylistIndex = 0;
        }
    }

    public int queueGetPosition() {
        if (mPlaylist == null || mPlaylist.length == 0) {
            return -1;
        }

        switch (mShuffleMode) {
            case SHUFFLE_NONE:
                return mPlaylistIndex;
            case SHUFFLE_AUTO:
                return mShuffledPlaylistIndexList.get(mShuffledPlaylistIndex);
        }

        return 0;
    }


    public int queueGetSize() {
        return mPlaylist.length;
    }


    public synchronized void setAutoStopTimestamp(long msecs) {
        if (msecs < 0) {
            mAutostopTimestamp = null;
        }
        else {
            mAutostopTimestamp = new Date();
            mAutostopTimestamp.setTime(mAutostopTimestamp.getTime() + msecs);
        }
    }

    public synchronized long getAutostopTimestamp() {
        if (mAutostopTimestamp == null) {
            return -1;
        }

        long remaining = mAutostopTimestamp.getTime() - (new Date().getTime());

        if (remaining < 0) {
            return 0;
        }

        return remaining;
    }

    public void notifyPlay() {
        mUiUpdateExecutor.submit(runnablePlay);

        mPlayState = PLAYSTATE_PLAYING;
        ProviderEventBus.getInstance().post(producePlaystateChangedEvent());
    }

    public void notifyPause(boolean keepNotification) {
        Runnable runnablePause = keepNotification ? runnablePauseKeepingNotification : runnablePauseNotKeepingNotification;
        mUiUpdateExecutor.submit(runnablePause);

        mPlayState = PLAYSTATE_PAUSED;
        ProviderEventBus.getInstance().post(producePlaystateChangedEvent());
    }

    public void notifyStop() {
        mUiUpdateExecutor.submit(runnableStop);

        mPlayState = PLAYSTATE_STOPPED;
        ProviderEventBus.getInstance().post(producePlaystateChangedEvent());
    }

    private void notifyQueueChanged() {
        ProviderEventBus.getInstance().post(producePlaylistChangedEvent());

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, mPlaylistIndex);
        edit.apply();
    }

    private void notifyShuffleChange() {
        ProviderEventBus.getInstance().post(produceShuffleModeEvent());

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, mShuffleMode);
        edit.apply();
    }

    private void notifyRepeatChange() {
        ProviderEventBus.getInstance().post(produceRepeatModeEvent());

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, mRepeatMode);
        edit.apply();
    }

    private void notifySetQueuePosition() {
        mMediaCoverUri = null;

        String mediaTitle = null;
        String mediaArtist = null;
        String mediaAlbum = null;
        long mediaDuration = 0;

        if (mPlaylist.length > 0) {
            if (mPlaylist.length < mPlaylistIndex) {
                mPlaylistIndex = 0;
            }

            if (mPlaylistIndex < mPlaylist.length) {
                final MediaManager.Media media = mPlaylist[mPlaylistIndex];

                mMediaCoverUri = mPlaylist[mPlaylistIndex].artUri;
                mediaTitle = media.name;
                mediaArtist = media.artist;
                mediaAlbum = media.album;
                mediaDuration = media.duration;
            }
        }

        mMedia = new MediaChangedEvent(mediaTitle, mediaArtist, mediaAlbum, mediaDuration, mPlaylistIndex);
        ProviderEventBus.getInstance().post(produceMediaChangedEvent());

        mUiUpdateExecutor.submit(runnableRefreshSongData);

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, mPlaylistIndex);
        edit.apply();
    }

    private void notifyCoverLoaded() {
        ProviderEventBus.getInstance().post(produceMediaCoverChangedEvent());
    }


    @Produce public MediaCoverChangedEvent produceMediaCoverChangedEvent() {
        return new MediaCoverChangedEvent(mMediaCover);
    }


    @Produce public ShuffleModeChangedEvent produceShuffleModeEvent() {
        return new ShuffleModeChangedEvent(mShuffleMode);
    }

    @Produce public RepeatModeChangedEvent produceRepeatModeEvent() {
        return new RepeatModeChangedEvent(mRepeatMode);
    }

    @Produce public TimestampChangedEvent produceTimestampChangedEvent() {
        return mTimestampChangedEvent;
    }

    @Produce public MediaChangedEvent produceMediaChangedEvent() {
        return mMedia;
    }

    @Produce public PlayStateChangedEvent producePlaystateChangedEvent() {
        return new PlayStateChangedEvent(mPlayState);
    }

    @Produce public PlaylistChangedEvent producePlaylistChangedEvent() {
        return new PlaylistChangedEvent(PlayerApplication.playerMediaManager().getId(), 0, mPlaylist.length);
    }


    public static final PendingIntent APPWIDGET_REFRESH_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_APPWIDGET, PlayerService.ACTION_REFRESH_WIDGETS);



    public static final PendingIntent NOTIFICATION_PAUSE_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_NOTIFICATION, PlayerService.ACTION_TOGGLEPAUSE);

    public static final PendingIntent NOTIFICATION_NEXT_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_NOTIFICATION, PlayerService.ACTION_NEXT);

    public static final PendingIntent NOTIFICATION_PREV_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_NOTIFICATION, PlayerService.ACTION_PREVIOUS);

    public static final PendingIntent NOTIFICATION_STOP_INTENT = PlayerService.buildServiceIntent(PlayerService.COMMAND_SOURCE_NOTIFICATION, PlayerService.ACTION_STOP);




    public static final Intent TELEPHONY_PLAY_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_TELEPHONY, PlayerService.ACTION_PLAY);

    public static final Intent TELEPHONY_PAUSE_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_TELEPHONY, PlayerService.ACTION_PAUSE);

    public static final Intent CLIENT_TOGGLE_PLAY_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_CLIENT_APP, PlayerService.ACTION_TOGGLEPAUSE);

    public static final Intent CLIENT_NEXT_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_CLIENT_APP, PlayerService.ACTION_NEXT);

    public static final Intent CLIENT_PREVIOUS_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_CLIENT_APP, PlayerService.ACTION_PREVIOUS);

    public static final Intent CLIENT_STOP_INTENT = PlayerService.buildBroadcastIntent(PlayerService.COMMAND_SOURCE_CLIENT_APP, PlayerService.ACTION_STOP);



    public static PendingIntent buildServiceIntent(final String source, final String action) {
        final Context context = PlayerApplication.context;

        final ComponentName serviceName = new ComponentName(context, PlayerService.class);

        final Intent intent = new Intent(source);
        intent.setComponent(serviceName);
        intent.putExtra(PlayerService.COMMAND_KEY, action);

        PendingIntent pendingIntent = null;

        switch (action) {
            case ACTION_TOGGLEPAUSE:
                pendingIntent = PendingIntent.getService(context, 1, intent, 0);
                break;
            case ACTION_PLAY:
                pendingIntent = PendingIntent.getService(context, 2, intent, 0);
                break;
            case ACTION_PAUSE:
                pendingIntent = PendingIntent.getService(context, 3, intent, 0);
                break;
            case ACTION_NEXT:
                pendingIntent = PendingIntent.getService(context, 4, intent, 0);
                break;
            case ACTION_PREVIOUS:
                pendingIntent = PendingIntent.getService(context, 5, intent, 0);
                break;
            case ACTION_STOP:
                pendingIntent = PendingIntent.getService(context, 6, intent, 0);
                break;
            case ACTION_REFRESH_WIDGETS:
                pendingIntent = PendingIntent.getService(context, 7, intent, 0);
                break;
        }

        return pendingIntent;
    }

    public static Intent buildBroadcastIntent(final String source, final String action) {
        final Context context = PlayerApplication.context;

        final ComponentName serviceName = new ComponentName(context, PlayerService.class);

        final Intent intent = new Intent(source);
        intent.setComponent(serviceName);
        intent.putExtra(PlayerService.COMMAND_KEY, action);
        return intent;
    }
}
