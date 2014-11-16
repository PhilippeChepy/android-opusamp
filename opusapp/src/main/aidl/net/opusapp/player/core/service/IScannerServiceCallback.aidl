/*
 * IScannerServiceCallback.aidl
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

interface IScannerServiceCallback {
	void onStartScan();
	void onStopScan();
}
