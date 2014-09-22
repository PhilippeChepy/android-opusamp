/*
 * IPlayerService.aidl
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

import net.opusapp.player.core.service.IPlayerServiceListener;
import net.opusapp.player.core.service.IScannerServiceCallback;

interface IPlayerService {
	void play();
	void pause(boolean keepNotification);
	void stop();
	boolean next();
	boolean prev();

	boolean isPlaying();
	
	long getDuration();
	long getPosition();
	void setPosition(long position);
	
	int getShuffleMode();
	void setShuffleMode(int shuffleMode);
	
	int getRepeatMode();
	void setRepeatMode(int repeatMode);
	
	void queueAdd(String media);
	void queueMove(int indexFrom, int indexTo);
	void queueRemove(int position);
    void queueClear();
    void queueReload();
    void queueSetPosition(int position);
	int queueGetPosition();
	int queueGetSize();
    
    void registerPlayerCallback(IPlayerServiceListener playerServiceListener);
    void unregisterPlayerCallback(IPlayerServiceListener playerServiceListener);

    void notifyProviderChanged();
}
