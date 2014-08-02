/*
 * IPlayerServiceCallback.aidl
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

interface IPlayerServiceListener {
	void onPlay();
	void onPause();
	void onStop();
	void onSeek(long position);

	void onShuffleModeChanged();
	void onRepeatModeChanged();

	void onQueueChanged();
	void onQueuePositionChanged();
}
