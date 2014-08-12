/*
 * PrefixedFileNameGenerator.java
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
package eu.chepy.audiokit.ui.utils.uil;

import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;

public class PrefixedFileNameGenerator extends Md5FileNameGenerator {

    private String filePrefix;

    public PrefixedFileNameGenerator(String prefix) {
        filePrefix = prefix;
    }

    @Override
    public String generate(String imageUri) {
        return filePrefix + super.generate(imageUri);
    }
}
