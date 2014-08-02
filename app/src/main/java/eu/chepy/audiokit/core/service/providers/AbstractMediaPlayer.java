/*
 * AbstractCodec.java
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

package eu.chepy.audiokit.core.service.providers;

public interface AbstractMediaPlayer {



    /*
        Stream management
     */
	public AbstractMedia initializeContent(String mediaPath);
	public void preloadContent(AbstractMedia context);
	public void finalizeContent(AbstractMedia context);



    /*
        MediaPlayer management
     */
	public void playerSetContent(AbstractMedia context);
	public void playerPlay();
	public void playerPause(boolean setPaused);
	public void playerStop();
	public boolean playerIsPlaying();
	public void playerSeek(long position);
	public long playerGetPosition();
	public long playerGetDuration();


    /*
        Player status listener
     */
    public interface OnProviderCompletionListener {
        public void onCodecCompletion();
        public void onCodecTimestampUpdate(long newPosition);
    }

    void addCompletionListener(OnProviderCompletionListener listener);
    void removeCompletionListener(OnProviderCompletionListener listener);
    void resetListeners();
}
