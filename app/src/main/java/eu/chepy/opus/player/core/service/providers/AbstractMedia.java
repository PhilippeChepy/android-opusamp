/*
 * AbstractMedia.java
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

package eu.chepy.opus.player.core.service.providers;

public abstract interface AbstractMedia {
	public static final String TAG = AbstractMedia.class.getSimpleName();

	public String getMediaUri();
}
