/*
 * SquareImageView.java
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
package eu.chepy.opus.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;

public class SquareImageView extends LayoutSuppressingImageView {

	public static final String TAG = SquareImageView.class.getSimpleName();
	
	public SquareImageView(Context paramContext, AttributeSet paramAttributeSet) {
		super(paramContext, paramAttributeSet);
	}

	public void onMeasure(int paramInt1, int paramInt2) {
		super.onMeasure(paramInt1, paramInt2);
		int i = Math.min(getMeasuredWidth(), getMeasuredHeight());
		setMeasuredDimension(i, i);
	}
}
