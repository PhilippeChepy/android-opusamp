/*
 * CustomTextView.java
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
package eu.chepy.audiokit.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import eu.chepy.audiokit.ui.utils.TypefaceCache;

public class CustomTextView extends TextView {

    public CustomTextView(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoLight.ttf", context));
        }
    }
}