/*
 * LibraryAdapterFactory.java
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
package eu.chepy.opus.player.ui.adapter;

import eu.chepy.opus.player.R;

public class LibraryAdapterFactory {

    public final static String TAG = LibraryAdapterFactory.class.getSimpleName();



    public static final int ADAPTER_ALBUM_ARTIST = 1;

    public static final int ADAPTER_ALBUM = 2;

    public static final int ADAPTER_ALBUM_SIMPLE = 3;

    public static final int ADAPTER_ARTIST = 4;

    public static final int ADAPTER_GENRE = 5;

    public static final int ADAPTER_PLAYLIST = 6;

    public static final int ADAPTER_RECENT = 7;

    public static final int ADAPTER_SONG = 8;

    public static final int ADAPTER_SONG_SIMPLE = 9;

    public static final int ADAPTER_PLAYLIST_DETAILS = 10;

    public static final int ADAPTER_STORAGE = 11;



    public static LibraryAdapter build(LibraryAdapter.LibraryAdapterContainer viewContainer, int adapterType, int managerIndex, int[] columnIndexes) {
        switch (adapterType) {
            case ADAPTER_ALBUM:
            case ADAPTER_ALBUM_SIMPLE:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_double_line_thumbnailed,
                        new int[] {
                                columnIndexes[1],
                                columnIndexes[2]
                        },
                        new int[] {
                                R.id.line_one,
                                R.id.line_two
                        },
                        columnIndexes[0],
                        R.drawable.no_art_small,
                        R.id.image,
                        columnIndexes[3]);
            case ADAPTER_ALBUM_ARTIST:
            case ADAPTER_ARTIST:
            case ADAPTER_GENRE:
            case ADAPTER_RECENT:
            case ADAPTER_PLAYLIST:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_single_line,
                        new int[] {
                                columnIndexes[1]
                        },
                        new int[] {
                                R.id.line_one
                        },
                        -1,
                        -1,
                        -1,
                        columnIndexes[2]);
            case ADAPTER_SONG_SIMPLE:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_double_line,
                        new int[] {
                                columnIndexes[1],
                                columnIndexes[2]
                        },
                        new int[] {
                                R.id.line_one,
                                R.id.line_two
                        },
                        -1,
                        -1,
                        -1,
                        columnIndexes[3]);
            case ADAPTER_SONG:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_double_line_thumbnailed,
                        new int[] {
                                columnIndexes[1],
                                columnIndexes[2]
                        },
                        new int[] {
                                R.id.line_one,
                                R.id.line_two
                        },
                        columnIndexes[0],
                        R.drawable.no_art_small,
                        R.id.image,
                        columnIndexes[3]);
            case ADAPTER_STORAGE:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_double_line_thumbnailed,
                        new int[] {
                                columnIndexes[1],
                                columnIndexes[2]
                        },
                        new int[] {
                                R.id.line_one,
                                R.id.line_two
                        },
                        columnIndexes[3],
                        R.drawable.no_art_small,
                        R.id.image,
                        columnIndexes[4]);
            case ADAPTER_PLAYLIST_DETAILS:
                return new LibraryAdapter(
                        viewContainer,
                        adapterType,
                        managerIndex,
                        R.layout.view_item_double_line_dragable_thumbnailed,
                        new int[] {
                                columnIndexes[1],
                                columnIndexes[2]
                        },
                        new int[] {
                                R.id.line_one,
                                R.id.line_two
                        },
                        columnIndexes[0],
                        R.drawable.no_art_small,
                        R.id.image,
                        -1,
                        R.id.now_playing_indicator
                        );
            default:
                throw new IllegalArgumentException();
        }
    }
}
