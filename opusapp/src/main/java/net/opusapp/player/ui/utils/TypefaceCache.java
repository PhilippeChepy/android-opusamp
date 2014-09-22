/*
 * TypefaceCache.java
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
package net.opusapp.player.ui.utils;

import android.content.Context;
import android.graphics.Typeface;

import java.util.WeakHashMap;

public class TypefaceCache {
	public static final String TAG = TypefaceCache.class.getSimpleName();
	
	private static final WeakHashMap<String, Typeface> MAP = new WeakHashMap<String, Typeface>();

	public static Typeface getTypeface(String typefaceName, Context context) {
		Typeface localTypeface = MAP.get(typefaceName);
		
		if (localTypeface == null) {
			localTypeface = Typeface.createFromAsset(context.getAssets(), typefaceName);
			MAP.put(typefaceName, localTypeface);
		}
		return localTypeface;
	}
}
