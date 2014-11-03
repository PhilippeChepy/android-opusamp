/*
 * CustomBoldTextView.java
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
package net.opusapp.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import net.opusapp.player.ui.utils.TypefaceCache;

public class CustomBoldTextView extends TextView {

    public CustomBoldTextView(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoRegular.ttf", context));
        }
    }
}
