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
package net.opusapp.player.core.service.providers.local;

import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.utils.LogUtils;

public class LocalMedia extends MediaManager.Media {
	
	public static final String TAG = LocalMedia.class.getSimpleName();



    public long nativeContext = 0;

    public String sourceUri;



    private MediaManager.Player player;

    private boolean loaded = false;



    public LocalMedia(MediaManager.Player player, String sourceUri) {
        this.player = player;
        this.sourceUri = sourceUri;
    }

    @Override
    public void load() {
        if (!loaded) {
            LogUtils.LOGD(TAG, "loaded : " + sourceUri);
            loaded = player.loadMedia(this);
        }
    }

    @Override
    public void unload() {
        if (loaded) {
            LogUtils.LOGD(TAG, "unloaded : " + sourceUri);
            player.unloadMedia(this);
            loaded = false;
        }
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    protected void finalize() throws Throwable {
        unload();
        super.finalize();
    }

    @Override
    public String getUri() {
        return sourceUri;
    }
}
