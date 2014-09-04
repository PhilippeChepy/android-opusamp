/*
 * Metadata.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.opus.player.core.service.providers;

public class MediaMetadata {

    public int index;

    public String description;

    public String value;

    public boolean editable;

    public MediaMetadata(int index, String description, String value, boolean editable) {
        this.index = index;
        this.description = description;
        this.value = value;
        this.editable = editable;
    }
}
