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
package net.opusapp.player.core.service.providers;

public class MediaMetadata {

    public int mFieldType;

    public String mDescription;

    public String mValue;

    public EditType mEditable;

    private String mOriginalValue;


    public enum EditType {
        TYPE_STRING,
        TYPE_NUMERIC,
        TYPE_READONLY
    }


    public MediaMetadata(int fieldType, String description, String value, EditType editable) {
        mFieldType = fieldType;
        mDescription = description;
        mValue = value;
        mOriginalValue = value;
        mEditable = editable;
    }

    public boolean changed() {
        return mValue == null || !mValue.equals(mOriginalValue);
    }
}
