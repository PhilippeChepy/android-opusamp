/*
 * MusicConnector.java
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
package net.opusapp.player.ui.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.adapter.ux.MetadataListAdapter;
import net.opusapp.player.utils.LogUtils;

import java.util.ArrayList;

public class MusicConnector {
	
	public static final String TAG = "MusicConnector";







    public static boolean show_hidden = false;


    public static int album_artists_sort_order = AbstractMediaManager.Provider.ALBUM_ARTIST_NAME;

    public static int albums_sort_order = AbstractMediaManager.Provider.ALBUM_NAME;

    public static int artists_sort_order = AbstractMediaManager.Provider.ARTIST_NAME;

    public static int genres_sort_order = AbstractMediaManager.Provider.GENRE_NAME;

    public static int playlists_sort_order = AbstractMediaManager.Provider.PLAYLIST_NAME;

    public static int songs_sort_order = AbstractMediaManager.Provider.SONG_TRACK;

    public static int storage_sort_order = AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME;



    public static int details_songs_sort_order = AbstractMediaManager.Provider.SONG_TRACK;

    public static int details_playlist_sort_order = AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION;

    public static int details_albums_sort_order = AbstractMediaManager.Provider.ALBUM_NAME;


    /*
        General service control
     */
    public static boolean isPlaying() {
        if (PlayerApplication.playerService != null) {
            return PlayerApplication.playerService.isPlaying();
        }
        else {
            LogUtils.LOGService(TAG, "isPlaying", 0);
        }

        return false;
    }

    public static int getCurrentPlaylistPosition() {
        if (PlayerApplication.playerService != null) {
            return PlayerApplication.playerService.queueGetPosition();
        }
        else {
            LogUtils.LOGService(TAG, "getCurrentPlaylistPosition", 0);
        }

        return 0;
    }



    public static void sendPlayIntent() {
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
        localBroadcastManager.sendBroadcast(PlayerService.CLIENT_PLAY_INTENT);
    }

    public static void sendPauseIntent() {
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
        localBroadcastManager.sendBroadcast(PlayerService.CLIENT_PAUSE_INTENT);
    }

    public static void sendStopIntent() {
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
        localBroadcastManager.sendBroadcast(PlayerService.CLIENT_STOP_INTENT);
    }

    public static void sendPrevIntent() {
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
        localBroadcastManager.sendBroadcast(PlayerService.CLIENT_PREVIOUS_INTENT);
    }

    public static void sendNextIntent() {
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(PlayerApplication.context);
        localBroadcastManager.sendBroadcast(PlayerService.CLIENT_NEXT_INTENT);
    }

    public static boolean servicePlayAction() {
        boolean isPlaying = isPlaying();

        if (PlayerApplication.playerService != null) {
            if (PlayerApplication.playerService.queueGetSize() != 0) {
                if (isPlaying) {
                    PlayerApplication.playerService.pause(false);
                }
                else {
                    PlayerApplication.playerService.play();
                }
                return true;
            }
        }
        else {
            LogUtils.LOGService(TAG, "doPlayAction", 0);
        }
        return false;
    }

    public static void doRepeatAction() {
        if (PlayerApplication.playerService != null) {
            int repeatMode = PlayerApplication.playerService.getRepeatMode();

            switch (repeatMode) {
                case PlayerService.REPEAT_NONE:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_CURRENT);
                    break;
                case PlayerService.REPEAT_CURRENT:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_ALL);
                    break;
                case PlayerService.REPEAT_ALL:
                    PlayerApplication.playerService.setRepeatMode(PlayerService.REPEAT_NONE);
                    break;
            }
        }
        else {
            LogUtils.LOGService(TAG, "doRepeatAction", 0);
        }
    }

    public static void doShuffleAction() {
        if (PlayerApplication.playerService != null) {
            int shuffleMode = PlayerApplication.playerService.getShuffleMode();

            switch (shuffleMode) {
                case PlayerService.SHUFFLE_AUTO:
                    PlayerApplication.playerService.setShuffleMode(PlayerService.SHUFFLE_NONE);
                    break;
                case PlayerService.SHUFFLE_NONE:
                    PlayerApplication.playerService.setShuffleMode(PlayerService.SHUFFLE_AUTO);
                    break;
            }
        }
        else {
            LogUtils.LOGService(TAG, "doShuffleAction", 0);
        }
    }





    /*
        Context actions
     */
    public static boolean doContextActionPlay(AbstractMediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        return provider.play(sourceType, sourceId, sortOrder, position, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionPlayNext(AbstractMediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        return provider.playNext(sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        return provider.playlistAdd(null, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionAddToPlaylist(Activity hostActivity, final AbstractMediaManager.Provider.ContentType sourceType, final String sourceId, final int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(String playlistId) {
                LogUtils.LOGD(TAG, "trying to add to " + playlistId);
                provider.playlistAdd(playlistId, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
            }
        });
        return true;
    }

    public static boolean doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType sourceType, String sourceId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        provider.setProperty(sourceType, sourceId, AbstractMediaManager.Provider.ContentProperty.CONTENT_VISIBILITY_TOGGLE, null, null);

        return true;
    }

    public static boolean doContextActionMediaRemoveFromQueue(String playlistId, int position) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        provider.playlistRemove(playlistId, position);
        return true;
    }

    public static boolean doContextActionPlaylistClear(String playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        provider.playlistClear(playlistId);
        return true;
    }

    public static boolean doContextActionDetail(Activity hostActivity, AbstractMediaManager.Provider.ContentType contentType, String contentId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        int titleResId = R.string.alert_dialog_title_media_properties;
        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                titleResId = R.string.alert_dialog_title_album_properties;

                break;
            case CONTENT_TYPE_MEDIA:
                break;
            default:
                return false;
        }

        final Object metadataList = provider.getProperty(contentType, contentId, AbstractMediaManager.Provider.ContentProperty.CONTENT_METADATA_LIST);
        final MetadataListAdapter adapter = new MetadataListAdapter(hostActivity, (ArrayList<MediaMetadata>)metadataList);

        new AlertDialog.Builder(hostActivity)
                .setTitle(titleResId)
                .setIcon(R.drawable.ic_launcher)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setAdapter(adapter,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialoginterface, int i) {
                            }
                        }
                )
                .show();
        return true;
    }

    public static boolean doContextActionPlaylistDelete(final Activity hostActivity, final String playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                provider.playlistDelete(playlistId);
                if (hostActivity instanceof LibraryMainActivity) {
                    ((LibraryMainActivity)hostActivity).refresh();
                }
            }
        };

        final DialogInterface.OnClickListener newPlaylistNegativeOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // nothing to be done.
            }
        };

        final TextView textView = new TextView(hostActivity);

        new AlertDialog.Builder(hostActivity)
                .setTitle(R.string.alert_dialog_title_playlist_delete)
                .setMessage(R.string.alert_dialog_message_playlist_delete)
                .setView(textView)
                .setPositiveButton(android.R.string.ok, newPlaylistPositiveOnClickListener)
                .setNegativeButton(android.R.string.cancel, newPlaylistNegativeOnClickListener)
                .show();
        return true;
    }



    private static void doPrepareProviderSwitch() {
        if (PlayerApplication.libraryManagerIndex != PlayerApplication.playerManagerIndex) {
            boolean playing = PlayerApplication.playerService.isPlaying();

            if (playing) {
                PlayerApplication.playerService.stop();
            }

            PlayerApplication.playerManagerIndex = PlayerApplication.libraryManagerIndex;
            PlayerApplication.saveLibraryIndexes();
            PlayerApplication.playerService.queueReload();

            if (playing) {
                PlayerApplication.playerService.play();
            }
        }
    }



    /*

     */
    interface PlaylistManagementRunnable {
        public void run(String playlistId);
    }



    protected static void showPlaylistManagementDialog(final Activity hostActivity, final PlaylistManagementRunnable runnable) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        final Cursor playlistCursor = provider.buildCursor(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                new int[] {
                        AbstractMediaManager.Provider.PLAYLIST_ID,
                        AbstractMediaManager.Provider.PLAYLIST_NAME
                },
                new int[] {
                        AbstractMediaManager.Provider.PLAYLIST_ID
                },
                null,
                null,
                null
        );

        final int PLAYLIST_COLUMN_ID = 0;
        final int PLAYLIST_COLUMN_NAME = 1;

        if (playlistCursor != null) {
            final ArrayList<String> playlistItemIds = new ArrayList<String>();
            final ArrayList<String> playlistItemDescriptions = new ArrayList<String>();

            playlistItemIds.add(null);
            playlistItemDescriptions.add(hostActivity.getString(R.string.label_new_playlist));

            while (playlistCursor.moveToNext()) {
                playlistItemIds.add(playlistCursor.getString(PLAYLIST_COLUMN_ID));
                playlistItemDescriptions.add(playlistCursor.getString(PLAYLIST_COLUMN_NAME));
            }

            final DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {
                final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
                final AbstractMediaManager.Provider mediaProvider = mediaManager.getProvider();

                final EditText nameEditText = new EditText(hostActivity);

                final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Editable nameEditable = nameEditText.getText();
                        if (nameEditable != null) {
                            String playlistId = mediaProvider.playlistNew(nameEditable.toString());
                            if (playlistId != null) {
                                runnable.run(playlistId);

                                if (hostActivity instanceof LibraryMainActivity) {
                                    ((LibraryMainActivity)hostActivity).refresh();
                                }
                            }
                        }
                    }
                };

                final DialogInterface.OnClickListener newPlaylistNegativeOnClickListener = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // nothing to be done.
                    }
                };

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        // New playlist
                        new AlertDialog.Builder(hostActivity)
                                .setTitle(R.string.label_new_playlist)
                                .setView(nameEditText)
                                .setPositiveButton(android.R.string.ok, newPlaylistPositiveOnClickListener)
                                .setNegativeButton(android.R.string.cancel, newPlaylistNegativeOnClickListener)
                                .show();
                    }
                    else {
                        runnable.run(playlistItemIds.get(which));
                    }
                }
            };

            new AlertDialog.Builder(hostActivity)
                    .setTitle(R.string.alert_dialog_title_add_to_playlist)
                    .setItems(playlistItemDescriptions.toArray(new String[playlistItemDescriptions.size()]), dialogOnClickListener)
                    .show();
        }
    }



    //
    public static void addLibrary(final Activity parent, final Runnable completionRunnable) {
        final ArrayList<Integer> managerItemIds = new ArrayList<Integer>();
        final ArrayList<String> managerItemDescriptions = new ArrayList<String>();

        final MediaManagerFactory.MediaManagerDescription managerList[] = MediaManagerFactory.getMediaManagerList();

        for (MediaManagerFactory.MediaManagerDescription mediaManager : managerList) {
            if (mediaManager != null && mediaManager.isEnabled) {
                managerItemIds.add(mediaManager.typeId);
                managerItemDescriptions.add(mediaManager.description);
            }
        }

        final EditText nameEditText = new EditText(parent);

        final DialogInterface.OnClickListener newLibraryOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // nothing to be done.
                SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();
                final Editable collectionName = nameEditText.getText();

                int mediaProviderType = managerItemIds.get(which);

                if (database != null && collectionName != null) {
                    String columns[] = new String[] {
                            "COUNT(*) AS " + Entities.Provider._COUNT
                    };

                    Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, null);
                    long count = 0;
                    if (cursor != null) {
                        if (cursor.getCount() > 0) {
                            cursor.moveToFirst();
                            count = cursor.getLong(0);
                        }
                        cursor.close();
                    }

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName.toString());
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, mediaProviderType);
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, count + 1);

                    long rowId = database.insert(Entities.Provider.TABLE_NAME, null, contentValues);
                    if (rowId < 0) {
                        LogUtils.LOGW(TAG, "new library: database insertion failure.");
                    } else {
                        configureLibrary(parent, (int) rowId, mediaProviderType);
                        completionRunnable.run();
                    }
                }
            }
        };

        final DialogInterface.OnClickListener onMediaManagerTypeSelection = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                editLibrary(parent, nameEditText, newLibraryOnClickListener, which);
            }
        };

        new AlertDialog.Builder(parent)
                .setTitle(R.string.alert_dialog_title_type_of_library)
                .setItems(managerItemDescriptions.toArray(new String[managerItemDescriptions.size()]), onMediaManagerTypeSelection)
                .show();
    }

    public static void editLibrary(Activity parent, final EditText nameEditText, final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener, final int itemType) {
        final InputMethodManager inputMethodManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);

        final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                newPlaylistPositiveOnClickListener.onClick(dialogInterface, itemType);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        };

        final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        };

        new AlertDialog.Builder(parent)
                .setTitle(R.string.label_new_library)
                .setView(nameEditText)
                .setPositiveButton(android.R.string.ok, positiveClickListener)
                .setNegativeButton(android.R.string.cancel, negativeClickListener)
                .show();

        nameEditText.requestFocus();
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    public static void configureLibrary(Activity sourceActivity, int mediaProviderId, int mediaProviderType) {
        LogUtils.LOGD(TAG, "providerId : " + mediaProviderId + " providerType : " + mediaProviderType);

        final AbstractMediaManager localLibraryProvider = MediaManagerFactory.buildMediaManager(mediaProviderType, mediaProviderId);
        final AbstractMediaManager.Provider provider = localLibraryProvider.getProvider();
        final AbstractMediaManager.ProviderAction providerAction = provider.getSettingsAction();

        if (providerAction != null) {
            /* launch activity */ providerAction.launch(sourceActivity);
        }
    }


    /*

     */
    public static View.OnClickListener repeatClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            MusicConnector.doRepeatAction();
        }
    };

    public static View.OnClickListener prevClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            sendPrevIntent();
        }
    };

    public static View.OnClickListener nextClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            sendNextIntent();
        }
    };

    public static View.OnClickListener shuffleClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            MusicConnector.doShuffleAction();
        }
    };

    public static class PlayClickListenerImpl implements View.OnClickListener {

        private static Activity currentActivity;

        public PlayClickListenerImpl(Activity targetActivity) {
            currentActivity = targetActivity;
        }

        @Override
        public void onClick(View view) {
            if (!MusicConnector.servicePlayAction()) {
                currentActivity.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        AlertDialog alertDialog = new AlertDialog.Builder(currentActivity).create();
                        alertDialog.setTitle(R.string.alert_dialog_title_playlist_is_empty);
                        alertDialog.setMessage(currentActivity.getString(R.string.alert_dialog_message_playlist_is_empty));
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // here you can add functions
                            }
                        });
                        alertDialog.setIcon(R.drawable.ic_launcher);
                        alertDialog.show();
                    }
                });
            }
        }
    }
}
