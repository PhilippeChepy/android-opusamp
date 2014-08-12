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
package eu.chepy.audiokit.core.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import eu.chepy.audiokit.core.NotificationHelper;
import eu.chepy.audiokit.core.RemoteControlClientHelper;
import eu.chepy.audiokit.core.service.providers.AbstractMedia;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaPlayer;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.utils.uil.ProviderImageDownloader;
import eu.chepy.audiokit.ui.widgets.Widget4x1;
import eu.chepy.audiokit.ui.widgets.Widget4x2;
import eu.chepy.audiokit.utils.LogUtils;


public class PlayerService extends Service implements AbstractMediaPlayer.OnProviderCompletionListener {

	private final static String TAG = "PlayerService";
	
	public static final int SHUFFLE_NONE = 0;
	
	public static final int SHUFFLE_AUTO = 1;
	
	public static final int REPEAT_NONE = 0;
	
	public static final int REPEAT_CURRENT = 1;
	
	public static final int REPEAT_ALL = 2;


    public static final String COMMAND_KEY = "command";

    public static final String ACTION_APPWIDGET_COMMAND = "eu.chepy.audiokit.service.APPWIDGET";

    public static final String ACTION_NOTIFICATION_COMMAND = "eu.chepy.audiokit.service.NOTIFICATION";

	public static final String ACTION_TOGGLEPAUSE = "eu.chepy.audiokit.TOGGLE_PAUSE";
	
	public static final String ACTION_NEXT = "eu.chepy.audiokit.NEXT";
	
	public static final String ACTION_PREVIOUS = "eu.chepy.audiokit.PREVIOUS";
	
	public static final String ACTION_STOP = "eu.chepy.audiokit.STOP";



    private int[] requestedFields = new int[] {
            AbstractMediaProvider.SONG_ID,
            AbstractMediaProvider.SONG_URI,
            AbstractMediaProvider.SONG_TITLE,
            AbstractMediaProvider.SONG_ARTIST,
            AbstractMediaProvider.SONG_ALBUM,
            AbstractMediaProvider.PLAYLIST_ENTRY_POSITION,
            AbstractMediaProvider.SONG_DURATION
    };

    private int[] sortOrder = new int[] {
            AbstractMediaProvider.PLAYLIST_ENTRY_POSITION
    };

    private Cursor playlist = null;

    private ArrayList<Integer> playlistOrder;

    private int playlistOrderIndex;



    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_URI = 1;

    private static final int COLUMN_SONG_TITLE = 2;

    private static final int COLUMN_SONG_ARTIST = 3;

    private static final int COLUMN_SONG_ALBUM = 4;

    private static final int COLUMN_PLAYLIST_ENTRY_POSITION = 5;

    private static final int COLUMN_SONG_DURATION = 6;



    private final Widget4x1 widgetMedium = Widget4x1.getInstance();

    private final Widget4x2 widgetLarge = Widget4x2.getInstance();



    public Context context;

	private RemoteCallbackList<IPlayerServiceListener> listeners = new RemoteCallbackList<IPlayerServiceListener>();

	private AbstractMedia currentTrack;

	private Lock broadcastMutex = new ReentrantLock();
	
	int repeatMode = REPEAT_NONE;
	
	int shuffleMode = SHUFFLE_NONE;

	private WakeLock wakelock;
	
	private NotificationHelper notificationHelper;

    private RemoteControlClientHelper remoteControlClient;

    private AudioManager audioManager;
	
	protected AbstractMedia unloadTrack(AbstractMedia track) {
		if (track != null) {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			if (track == currentTrack) {
				mediaPlayer.playerStop();
                mediaPlayer.finalizeContent(currentTrack);
			}
            mediaPlayer.finalizeContent(track);
		}
		return null;
	}
	
	protected AbstractMedia loadTrack() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

		return mediaPlayer.initializeContent(playlist.getString(COLUMN_SONG_URI));
	}
	
	protected void initPlaylist() {
		if (playlist != null) {
			playlist.close();
		}

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

		playlist = mediaProvider.buildCursor(
                AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA,
                requestedFields,
                sortOrder,
                null,
                AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);

        int playlistLength = playlist != null ? playlist.getCount() : 0;

        playlistOrder = new ArrayList<Integer>();
        for (int playlistIndex = 0 ; playlistIndex < playlistLength ; playlistIndex++) {
            playlistOrder.add(playlistIndex);
        }
        Collections.shuffle(playlistOrder);
        playlistOrderIndex = 0;
	}

    protected void doUpdateWidgets() {
        Log.d(TAG, "doUpdateWidgets()");
        final SongInformations currentSong = new SongInformations();

        widgetLarge.notifyChange(PlayerService.this, currentSong.initialized, currentSong.trackName, currentSong.artistName, currentSong.albumName, currentSong.art);
        widgetMedium.notifyChange(PlayerService.this, currentSong.initialized, currentSong.trackName, currentSong.artistName, currentSong.albumName, currentSong.art);
    }

    protected void doSetProviderListeners() {
        for (int providerIndex = 0 ; providerIndex < PlayerApplication.mediaManagers.length ; providerIndex++) {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[providerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

            mediaPlayer.addCompletionListener(this);
        }
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand()");

        if (intent != null) {
            if (intent.hasExtra(COMMAND_KEY)) {
                MusicConnector.doManageControlIntent(intent);
            }
        }

		return Service.START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
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
                            binder.pause(false);
                        } catch (final Exception exception) {
                            LogUtils.LOGException(TAG, "setAudioListener", 0, exception);
                        }
                    }
                }
            };
        }
    }

	@Override
	public void onCreate() {
		super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        setAudioListener();

		notificationHelper = new NotificationHelper(this);
        remoteControlClient = new RemoteControlClientHelper();
	    registerReceiver(broadcastReceiver, new IntentFilter(ACTION_NOTIFICATION_COMMAND));

        /**/
        context = getApplicationContext();

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "whatever");

        Context applicationContext = getApplicationContext();
        int position = 0;

        if (applicationContext != null) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
            repeatMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, REPEAT_NONE);
            shuffleMode = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, SHUFFLE_NONE);
            //initPlaylist(sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_ID, 0));
            position = sharedPreferences.getInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, 0);
        }
        else {
            repeatMode = REPEAT_NONE;
            shuffleMode = SHUFFLE_NONE;
            //initPlaylist(0);
        }
        initPlaylist();

        if (playlist != null && playlist.getCount() > 0) {
            try {
                if (position < 0) {
                    position = 0;
                }

                binder.queueSetPosition(position);
            } catch (final RemoteException remoteException) {
                Log.w(TAG, "Exception in onStartCommand() : " + remoteException.getMessage());
            }
        }

        doSetProviderListeners();
        doUpdateWidgets();
	}
	
	@Override
	public void onDestroy() {
		unregisterReceiver(broadcastReceiver);
	    
	    stopForeground(true);
	    hasNotification = false;
	}

	@Override
	public void onCodecCompletion() {
		Log.d(TAG, "onCodecCompletion()");
		try {
			switch (repeatMode) {
			case REPEAT_NONE:
				binder.next();
				if (playlist.getPosition() == 0) {
					binder.stop(); /* for service notification to clients */
				}
				else {
					binder.play();
				}
				break;
			case REPEAT_ALL:
				binder.next();
				binder.play();
				break;
			case REPEAT_CURRENT:
				binder.play();
				break;
			}
		} catch (final RemoteException exception) {
			Log.d(TAG, "onCodecCompletion() : " + exception.getMessage());
		}
	}
	
	@Override
	public void onCodecTimestampUpdate(final long newPosition) {
		binder.notifySetPosition(newPosition);
	}

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

	
	private final BroadcastReceiver broadcastReceiver = new ServiceIntentReceiver();

	private final IPlayerServiceStubImpl binder = new IPlayerServiceStubImpl();

	public boolean hasNotification;
	
	class IPlayerServiceStubImpl extends IPlayerService.Stub {

		@Override
		public void registerPlayerCallback(IPlayerServiceListener playerServiceCallback) throws RemoteException {
			Log.d(TAG, "registerCallback()");
			if (!listeners.register(playerServiceCallback)) {
				throw new RemoteException();
			}
		}

		@Override
		public void unregisterPlayerCallback(IPlayerServiceListener playerServiceCallback) throws RemoteException {
			Log.d(TAG, "unregisterCallback()");
			if (!listeners.unregister(playerServiceCallback)) {
				throw new RemoteException();
			}
		}

        @Override
        public void notifyProviderChanged() {
            doSetProviderListeners();
        }

        @TargetApi(14)
		private void notifyPlay() {
            int audioFocus = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            if (PlayerApplication.hasICS()) {
                audioFocus = audioManager.requestAudioFocus(
                        audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }

            remoteControlClient.register(PlayerService.this, audioManager);
            if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (hasNotification) {
                    notificationHelper.goToIdleState(true);
                }
                else {
                    final SongInformations currentSong = new SongInformations();

                    remoteControlClient.updateMetadata(
                            currentSong.art,
                            currentSong.trackName,
                            currentSong.artistName,
                            currentSong.albumName,
                            playlist.getLong(COLUMN_SONG_DURATION));

                    notificationHelper.buildNotification(
                            currentSong.albumName,
                            currentSong.artistName,
                            currentSong.trackName,
                            currentSong.art);

                    doUpdateWidgets();

                    startForeground(PlayerApplication.NOTIFICATION_PLAY_ID, notificationHelper.getNotification());
                    hasNotification = true;
                }
                remoteControlClient.updateState(true);

                broadcastMutex.lock();
                final int broadcastItemCount = listeners.beginBroadcast();
                for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
                    try {
                        listeners.getBroadcastItem(itemIndex).onPlay();
                    }
                    catch (final RemoteException remoteException) {
                        LogUtils.LOGException(TAG, "notifyPlay()", 0, remoteException);
                    }
                }
                listeners.finishBroadcast();
                broadcastMutex.unlock();
            }
		}

        @TargetApi(14)
		private void notifyPause(boolean keepNotification) {
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
			
			broadcastMutex.lock();
			final int broadcastItemCount = listeners.beginBroadcast();
	        for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
	            try {
	            	listeners.getBroadcastItem(itemIndex).onPause();
	            }  
	            catch (final RemoteException remoteException) {
	            	LogUtils.LOGException(TAG, "notifyPause()", 0, remoteException);
	            } 
	        } 
	        listeners.finishBroadcast();
	        broadcastMutex.unlock();
		}

        @TargetApi(14)
		private void notifyStop() {
			stopForeground(true);
            remoteControlClient.stop();
            remoteControlClient.release();

            doUpdateWidgets();

            if (PlayerApplication.hasICS()) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
			hasNotification = false;
			
			broadcastMutex.lock();
			final int broadcastItemCount = listeners.beginBroadcast();
	        for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
	            try {
	            	listeners.getBroadcastItem(itemIndex).onStop();
	            }  
	            catch (final RemoteException remoteException) {
	            	LogUtils.LOGException(TAG, "notifyStop()", 0, remoteException);
	            } 
	        } 
	        listeners.finishBroadcast();
	        broadcastMutex.unlock();
		}
		
		private void notifyShuffleChange() {
			broadcastMutex.lock();
			final int broadcastItemCount = listeners.beginBroadcast();
	        for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
	            try {
	            	listeners.getBroadcastItem(itemIndex).onShuffleModeChanged();
	            }  
	            catch (final RemoteException remoteException) {
	            	LogUtils.LOGException(TAG, "notifyShuffleChange()", 0, remoteException);
	            } 
	        } 
	        listeners.finishBroadcast();
	        broadcastMutex.unlock();
		}
		
		private void notifyRepeatChange() {
			broadcastMutex.lock();
			final int broadcastItemCount = listeners.beginBroadcast();
	        for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
	            try {
	            	listeners.getBroadcastItem(itemIndex).onRepeatModeChanged();
	            }  
	            catch (final RemoteException remoteException) {
	            	LogUtils.LOGException(TAG, "notifyRepeatChange()", 0, remoteException);
	            } 
	        } 
	        listeners.finishBroadcast();
	        broadcastMutex.unlock();
		}

        private void notifySetQueuePosition() {
            hasNotification = false;

            doUpdateWidgets();

            broadcastMutex.lock();
            final int broadcastItemCount = listeners.beginBroadcast();
            for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
                try {
                    listeners.getBroadcastItem(itemIndex).onQueuePositionChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifySetQueuePosition()", 0, remoteException);
                }
            }
            listeners.finishBroadcast();
            broadcastMutex.unlock();
        }

        private void notifyQueueChanged() {
            hasNotification = false;

            doUpdateWidgets();

            broadcastMutex.lock();
            final int broadcastItemCount = listeners.beginBroadcast();
            for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
                try {
                    listeners.getBroadcastItem(itemIndex).onQueueChanged();
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "notifyQueueChanged()", 0, remoteException);
                }
            }
            listeners.finishBroadcast();
            broadcastMutex.unlock();
        }
		
		public void notifySetPosition(long position) {
			broadcastMutex.lock();
			final int broadcastItemCount = listeners.beginBroadcast();

	        for (int itemIndex = 0 ; itemIndex < broadcastItemCount; itemIndex++) {
	            try {
	            	listeners.getBroadcastItem(itemIndex).onSeek(position);
	            }  
	            catch (final RemoteException remoteException) {
	            	LogUtils.LOGException(TAG, "notifySetPosition()", 0, remoteException);
	            } 
	        } 
	        listeners.finishBroadcast();
	        broadcastMutex.unlock();
		}

		@Override
		public synchronized void play() throws RemoteException {
			Log.d(TAG, "playerPlay()");

            if (playlist == null || playlist.getCount() == 0) {
                return;
            }

			if (isPlaying()) {
				return;
			}
			
			if (currentTrack == null) {
				if (playlist.getCount() > 0) {
					currentTrack = loadTrack();
				}
			}

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			if (currentTrack != null) {
				mediaPlayer.playerSetContent(currentTrack);
			}

            mediaPlayer.playerPlay();
            wakelock.acquire();
            notifyPlay();
		}

		@Override
		public synchronized void pause(boolean keepNotification) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			if (mediaPlayer.playerIsPlaying()) {
				mediaPlayer.playerPause(true);
				notifyPause(keepNotification);
				wakelock.release();
			}
		}

		@Override
		public synchronized void stop() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			mediaPlayer.playerStop();
			if (currentTrack != null) {
				currentTrack = unloadTrack(currentTrack);
			}

			notifyStop();
			if (wakelock.isHeld()) {
				wakelock.release();
			}
		}

		@Override
		public synchronized void next() throws RemoteException {
			if (playlist != null && playlist.getCount() > 1) {
				boolean wasPlaying = isPlaying();
				
				currentTrack = unloadTrack(currentTrack);

                if (shuffleMode == SHUFFLE_NONE) {
                    if (!playlist.moveToNext()) {
                        playlist.moveToFirst();
                    }
                }
                else if (shuffleMode == SHUFFLE_AUTO) {
                    playlistOrderIndex++;
                    if (playlistOrderIndex >= playlistOrder.size()) {
                        playlistOrderIndex = 0;
                    }

                    playlist.moveToPosition(playlistOrder.get(playlistOrderIndex));
                }

				final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
				final Editor edit = sharedPreferences.edit();
				edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
				edit.commit();
				
				notifySetQueuePosition();
				
				if (wasPlaying) {
					play();
				}
			}
		}

		@Override
		public synchronized void prev() throws RemoteException {
			if (playlist != null && playlist.getCount() > 1) {
				boolean wasPlaying = isPlaying();
				
				currentTrack = unloadTrack(currentTrack);
				
                if (shuffleMode == SHUFFLE_NONE) {
                    if (!playlist.moveToPrevious()) {
                        playlist.moveToLast();
                    }
                }
                else if (shuffleMode == SHUFFLE_AUTO) {
                    playlistOrderIndex--;
                    if (playlistOrderIndex < 0) {
                        playlistOrderIndex = playlistOrder.size() - 1;
                    }

                    playlist.moveToPosition(playlistOrder.get(playlistOrderIndex));
                }

				
				final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
				final Editor edit = sharedPreferences.edit();
				edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
				edit.commit();

                notifySetQueuePosition();
				
				if (wasPlaying) {
					play();
				}
			}
		}

		@Override
		public synchronized boolean isPlaying() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			return mediaPlayer.playerIsPlaying();
		}

		@Override
		public synchronized long getDuration() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			return mediaPlayer.playerGetDuration();
		}

		@Override
		public synchronized long getPosition() throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			return mediaPlayer.playerGetPosition();
		}

		@Override
		public synchronized void setPosition(long position) throws RemoteException {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaPlayer mediaPlayer = mediaManager.getMediaPlayer();

			mediaPlayer.playerSeek(position);
			notifySetPosition(position);
		}

		@Override
		public int getShuffleMode() throws RemoteException {
			return shuffleMode;
		}

		@Override
		public synchronized void setShuffleMode(int mode) throws RemoteException {
			Log.d(TAG, "setShuffleMode() : " + mode);
			
			shuffleMode = mode;
			if (shuffleMode == SHUFFLE_AUTO) {
				if (getRepeatMode() == REPEAT_CURRENT) {
					setRepeatMode(REPEAT_ALL);
				}
			}
			
			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			final Editor edit = sharedPreferences.edit();
			edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_SHUFFLE_MODE, shuffleMode);
			edit.commit();
			
			notifyShuffleChange();
		}

		@Override
		public synchronized int getRepeatMode() throws RemoteException {
			return repeatMode;
		}

		@Override
		public synchronized void setRepeatMode(int mode) throws RemoteException {
			Log.d(TAG, "setRepeatMode() : " + mode);

			repeatMode = mode;
			if (repeatMode == REPEAT_CURRENT) {
				if (getShuffleMode() != SHUFFLE_NONE) {
					setShuffleMode(SHUFFLE_NONE);
				}
			}
			
			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			final Editor edit = sharedPreferences.edit();
			edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_REPEAT_MODE, repeatMode);
			edit.commit();
			notifyRepeatChange();
		}

		@Override
		public synchronized int queueGetPosition() throws RemoteException {
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
		public synchronized int queueGetSize() throws RemoteException {
			return playlist.getCount();
		}

		@Override
		public synchronized void queueSetPosition(int position) throws RemoteException {
			Log.d(TAG, "setQueuePosition()");
			
			if (playlist != null && playlist.getCount() > 0) {
				Log.d(TAG, "setQueuePosition() : moving to position " + position);

                if (shuffleMode == SHUFFLE_NONE) {
                    if (playlist.getPosition() != position) {
                        currentTrack = unloadTrack(currentTrack);
                        if (!playlist.moveToPosition(position)) {
                            playlist.moveToFirst();
                        }
                    }
                }
                else if (shuffleMode == SHUFFLE_AUTO) {
                    int currentTrackIndex = playlistOrder.get(playlistOrderIndex);
                    if (currentTrackIndex != position) {
                        currentTrack = unloadTrack(currentTrack);

                        // TODO: better indexing ?
                        int indexOfPosition = playlistOrder.indexOf(position);
                        playlistOrder.set(indexOfPosition, currentTrackIndex);
                        playlistOrder.set(playlistOrderIndex, position);

                        playlist.moveToPosition(position);
                    }
                }

				final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
				final Editor edit = sharedPreferences.edit();
				edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
				edit.commit();
				
				notifySetQueuePosition();
			}
		}

		@Override
		public synchronized void queueAdd(String mediaId) throws RemoteException {
			Log.d(TAG, "queueAdd()");

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
			mediaProvider.playlistAdd(null, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, mediaId, 0, null);

            int position = playlist.getPosition();

			initPlaylist();

            if (!playlist.moveToPosition(position)) {
                playlist.moveToLast();
            }
			
			if (playlist.getCount() == 1) {
				play();
			}

            notifyQueueChanged();
		}

		@Override
		public synchronized void queueMove(int indexFrom, int indexTo) throws RemoteException {
			Log.d(TAG, "queueMove()");
			
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
            final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
			mediaProvider.playlistMove(null, indexFrom, indexTo);
			
			initPlaylist();
			
			if (!playlist.moveToPosition(position)) {
				playlist.moveToLast();
			}
			
			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			final Editor edit = sharedPreferences.edit();
			edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
			edit.apply();

            notifyQueueChanged();
		}

		@Override
		public synchronized void queueRemove(int entry) throws RemoteException {
			Log.d(TAG, "queueRemove()");
			
			int position = playlist.getPosition();
			if (entry < position) {
				position--;
			}

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
			mediaProvider.playlistRemove(null, entry);
		
			boolean wasPlaying = isPlaying();
			
			if (position == entry) {
				currentTrack = unloadTrack(currentTrack);
			}
			
			initPlaylist();
			
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
			
			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			final Editor edit = sharedPreferences.edit();
			edit.putInt(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION, playlist.getPosition());
			edit.apply();

            notifyQueueChanged();
		}

		@Override
		public synchronized void queueClear() throws RemoteException {
			Log.d(TAG, "queueClear()");
			
			if (isPlaying()) {
				currentTrack = unloadTrack(currentTrack);
			}

            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
            final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

            mediaProvider.playlistClear(null);
			initPlaylist();

			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			final Editor edit = sharedPreferences.edit();
			edit.remove(PlayerApplication.PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION);
			edit.apply();

            notifyQueueChanged();
		}

		@Override
		public void queueReload() throws RemoteException {
			final int position = playlist.getPosition();
			
			boolean wasPlaying = isPlaying();
			
			if (wasPlaying) {
				pause(true);
			}				
			
			initPlaylist();
			
			if (!playlist.moveToPosition(position)) {
				playlist.moveToFirst();
			}
			
			if (currentTrack != null && playlist.getCount() > 0) {
                final String mediaUri = currentTrack.getMediaUri();
				if (!TextUtils.isEmpty(mediaUri) && mediaUri.equals(playlist.getString(COLUMN_SONG_URI))) {
					if (wasPlaying) {
						play();
					}
				}
				else {
					currentTrack = unloadTrack(currentTrack);
				}
			}

            notifyQueueChanged();
		}
	}

    public class SongInformations {
        String albumName;

        String artistName;

        String trackName;

        int songId;

        String songArtUri;
        Bitmap art;

        boolean initialized;

        public SongInformations() {
            if (playlist != null && playlist.getCount() > 0) {
                albumName = playlist.getString(COLUMN_SONG_ALBUM);
                artistName = playlist.getString(COLUMN_SONG_ARTIST);
                trackName = playlist.getString(COLUMN_SONG_TITLE);

                songId = playlist.getInt(COLUMN_SONG_ID);

                songArtUri = ProviderImageDownloader.SCHEME_URI_PREFIX +
                        ProviderImageDownloader.SUBTYPE_MEDIA + "/" +
                        PlayerApplication.playerManagerIndex + "/" +
                        songId;

                art = null;
                if (!TextUtils.isEmpty(songArtUri)) {
                    art = PlayerApplication.normalImageLoader.loadImageSync(songArtUri);

                    if (art != null && art.isRecycled()) {
                        art = null;
                    }
                }
                Log.d(TAG, "SongInformation.initialized : true");
                initialized = true;
            }
            else {
                Log.d(TAG, "SongInformation.initialized : false");
                initialized = false;
            }
        }
    }
}
