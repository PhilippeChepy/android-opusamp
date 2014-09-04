/*
 * LocalMediaPlayer.java
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

package eu.chepy.opus.player.core.service.providers.local;

import java.util.ArrayList;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.utils.LogUtils;
import eu.chepy.opus.player.utils.jni.JniMediaLib;

public class LocalPlayer extends JniMediaLib implements AbstractMediaManager.Player {

	public static final String TAG = LocalPlayer.class.getSimpleName();

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
        playing = false;

        for (OnProviderCompletionListener listener : listenerList) {
            listener.onCodecCompletion();
        }
	}

	public LocalPlayer(LocalMediaManager localMediaManager) {
        mediaManager = localMediaManager;
		if (engineInitialize() != 0) {
            LogUtils.LOGE(TAG, "unable to initialize engine");
		}
        else {
            LogUtils.LOGI(TAG, "initialized engine");
        }
	}
	
	@Override
	protected void finalize() throws Throwable {
		engineFinalize();
		super.finalize();
	}

	@Override
	public AbstractMediaManager.Media initializeContent(String mediaUri) {
		LocalMedia codecContext = new LocalMedia(mediaUri);
		codecContext.nativeContext = streamInitialize(mediaUri);
		
		return codecContext;
	}

    // TODO: use at "end - 25000ms" when playing content.
	@Override
	public void preloadContent(AbstractMediaManager.Media context) {
		if (context instanceof LocalMedia) {
			LocalMedia codecContext = (LocalMedia)context;
            streamPreload(codecContext.nativeContext);
		}
	}

	@Override
	public void finalizeContent(AbstractMediaManager.Media context) {
		if (context instanceof LocalMedia) {
			LocalMedia codecContext = (LocalMedia)context;
            streamFinalize(codecContext.nativeContext);
			codecContext.nativeContext = 0;
		}
	}

	@Override
	public synchronized void playerSetContent(AbstractMediaManager.Media context) {
		if (context instanceof LocalMedia) {
			if (currentContext != null && playing) {
				playerPause(true);
			}
			
			currentContext = (LocalMedia) context;
            LogUtils.LOGD(TAG, "new context=" + currentContext.nativeContext);
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
                LogUtils.LOGI(TAG, "Stopping streaming");
                streamStop(currentContext.nativeContext);
				playing = false;
			}
			else {
                LogUtils.LOGI(TAG, "Starting streaming");
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

                ((LocalProvider)(mediaManager.getProvider())).setLastPlayed(currentContext.getMediaUri());

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

    @Override
    public boolean equalizerIsEnabled() {
        return engineEqualizerIsEnabled();
    }

    @Override
    public long equalizerSetEnabled(boolean enabled) {
        return engineEqualizerSetEnabled(enabled);
    }

    @Override
    public long equalizerBandSetGain(int band, int gain) {
        return engineEqualizerBandSetValue(band, gain);
    }

    @Override
    public long equalizerBandGetGain(int band) {
        return engineEqualizerBandGetValue(band);
    }

    private ArrayList<OnProviderCompletionListener> listenerList = new ArrayList<OnProviderCompletionListener>();

	@Override
	public void addCompletionListener(OnProviderCompletionListener listener) {
        if (listenerList.indexOf(listener) < 0) {
            listenerList.add(listener);
        }
	}

	@Override
	public void removeCompletionListener(OnProviderCompletionListener listener) {
		listenerList.remove(listener);
	}

    @Override
    public void resetListeners() {
        listenerList.clear();
    }
}
