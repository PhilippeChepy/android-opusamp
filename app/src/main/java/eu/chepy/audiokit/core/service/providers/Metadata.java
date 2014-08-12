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
package eu.chepy.audiokit.core.service.providers;

public class Metadata {

    public int index;

    public String description;

    public String value;

    public boolean editable;

    public Metadata(int index, String description, String value, boolean editable) {
        this.index = index;
        this.description = description;
        this.value = value;
        this.editable = editable;
    }
}
