/*
 * JniCodec.java
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

package eu.chepy.audiokit.core.service.providers.local;

import android.util.Log;

import java.util.ArrayList;

import eu.chepy.audiokit.core.service.providers.AbstractMedia;
import eu.chepy.audiokit.core.service.providers.AbstractMediaPlayer;
import eu.chepy.audiokit.utils.jni.JniMediaLib;

public class LocalMediaPlayer extends JniMediaLib implements AbstractMediaPlayer {

	public static final String TAG = LocalMediaPlayer.class.getSimpleName();

	private static LocalMedia currentContext = null;
	
	private static boolean playing = false;
	
	private static final Object timestampMutex = new Object(); /* specific mutex for timestamp protection */

	private static long lastTimestampUpdate = 0;

    private LocalMediaManager mediaManager = null;



    @Override
	protected void playbackUpdateTimestamp(long timestamp) {
        if ((timestamp - lastTimestampUpdate) > 200) { /* every ~200 msecs, request an update */
            lastTimestampUpdate = timestamp;
            for (OnProviderCompletionListener listener : listenerList) {
                listener.onCodecTimestampUpdate(timestamp);
            }
        }
	}

    @Override
	protected void playbackEndNotification() {
		Log.d(TAG, "playbackEndNotification()");
		new Thread(
				new Runnable() {
					
					@Override
					public void run() {
						playerStop();
						Log.d(TAG, "playbackEndNotification() : stopped");
						for (OnProviderCompletionListener listener : listenerList) {
							listener.onCodecCompletion();
						}
						Log.d(TAG, "playbackEndNotification() : done");
					}
				}
				).start();
	}

	public LocalMediaPlayer(LocalMediaManager localMediaManager) {
        mediaManager = localMediaManager;
		if (engineInitialize() != 0) { // TODO: add ref counter to JniMediaLib.engineInitialize()
			Log.w(TAG, "unable to initialize engine");
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		// TODO: add ref counter JniMediaLib.engineFinalize()
		super.finalize();
	}

	@Override
	public AbstractMedia initializeContent(String mediaUri) {
		LocalMedia codecContext = new LocalMedia(mediaUri);
		codecContext.nativeContext = streamInitialize(mediaUri);
		
		return codecContext;
	}

    // TODO: use at "end - 25000ms" when playing content.
	@Override
	public void preloadContent(AbstractMedia context) {
		if (context instanceof LocalMedia) {
			LocalMedia codecContext = (LocalMedia)context;
            streamPreload(codecContext.nativeContext);
		}
	}

	@Override
	public void finalizeContent(AbstractMedia context) {
		if (context instanceof LocalMedia) {
			LocalMedia codecContext = (LocalMedia)context;
            streamFinalize(codecContext.nativeContext);
			codecContext.nativeContext = 0;
		}
	}

	@Override
	public synchronized void playerSetContent(AbstractMedia context) {
		if (context instanceof LocalMedia) {
			if (currentContext != null && playing) {
				playerPause(true);
			}
			
			if (currentContext != null) {
				Log.d(TAG, "old context=" + currentContext.nativeContext);
			}
			else {
				Log.d(TAG, "old context=" + -1);
			}
			currentContext = (LocalMedia) context;
			Log.d(TAG, "new context=" + currentContext.nativeContext);
		}
	}

	@Override
	public synchronized void playerPlay() {
		if (currentContext != null) {
			synchronized (timestampMutex) {
				lastTimestampUpdate = 0;
			}

            if (currentContext.nativeContext != 0) {
                streamStart(currentContext.nativeContext);
                playing = true;
            }
            else {
                playbackEndNotification();
            }
		}
	}

	@Override
	public synchronized void playerPause(boolean setPaused) {
		if (currentContext != null) {
			if (setPaused) {
				Log.d(TAG, "Stopping streaming");
                streamStop(currentContext.nativeContext);
				playing = false;
			}
			else {
				Log.d(TAG, "Starting streaming");
                streamStart(currentContext.nativeContext);
				playing = true;
			}
		}
	}

	@Override
	public synchronized void playerStop() {
		if (currentContext != null) {
			if (playing && currentContext.nativeContext != 0) {
                streamStop(currentContext.nativeContext);
                streamSetPosition(currentContext.nativeContext, 0);

                ((LocalMediaProvider)(mediaManager.getMediaProvider())).setLastPlayed(currentContext.getMediaUri());

				playing = false;
			}

			synchronized (timestampMutex) {
				lastTimestampUpdate = 0;
			}
		}
	}

	@Override
	public synchronized boolean playerIsPlaying() {
		return playing;
	}

	@Override
	public synchronized void playerSeek(long position) {
		if (currentContext != null) {
            streamSetPosition(currentContext.nativeContext, position);
		}
	}

	@Override
	public synchronized long playerGetPosition() {
		return lastTimestampUpdate;
	}
	
	@Override
	public synchronized long playerGetDuration() {
		if (currentContext != null) {
			return streamGetDuration(currentContext.nativeContext);
		}
		
		return -1;
	}
	
	private ArrayList<OnProviderCompletionListener> listenerList = new ArrayList<OnProviderCompletionListener>();

	@Override
	public void addCompletionListener(OnProviderCompletionListener listener) {
		Log.d(TAG, "addCompletionListener()");
        if (listenerList.indexOf(listener) < 0) {
            listenerList.add(listener);
        }
	}

	@Override
	public void removeCompletionListener(OnProviderCompletionListener listener) {
		Log.d(TAG, "removeCompletionListener()");
		listenerList.remove(listener);
	}

    @Override
    public void resetListeners() {
        Log.d(TAG, "resetListeners()");
        listenerList.clear();
    }
}
