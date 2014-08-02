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
package eu.chepy.audiokit.core.service.providers.local;

import eu.chepy.audiokit.core.service.providers.AbstractMedia;

public class LocalMedia implements AbstractMedia {
	
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
