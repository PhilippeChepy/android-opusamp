/*
 * JniCodecContext.java
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

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;

public class LocalMedia implements AbstractMediaManager.Media {
	
	public static final String TAG = "JniCodecContext";

    private String mediaUri;

	public long nativeContext = 0;

    public LocalMedia(String mediaUri) {
        this.mediaUri = mediaUri;
    }

    @Override
    public String getMediaUri() {
        return mediaUri;
    }
}
