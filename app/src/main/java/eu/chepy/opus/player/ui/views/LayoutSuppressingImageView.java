/*
 * LayoutSuppressingImageView.java
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
package eu.chepy.opus.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class LayoutSuppressingImageView extends ImageView {
	
	public static final String TAG = LayoutSuppressingImageView.class.getSimpleName();
	
	public LayoutSuppressingImageView(Context paramContext, AttributeSet paramAttributeSet) {
		super(paramContext, paramAttributeSet);
	}

	public void requestLayout() {
		forceLayout();
	}
}
