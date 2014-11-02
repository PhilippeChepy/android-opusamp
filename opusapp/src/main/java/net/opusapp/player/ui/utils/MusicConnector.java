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
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.RemoteException;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
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

    public static int details_albums_sort_order = AbstractMediaManager.Provider.ALBUM_NAME;


    /*
        General service control
     */
    public static boolean isPlaying() {
        if (PlayerApplication.playerService != null) {
            try {
                return PlayerApplication.playerService.isPlaying();
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "isPlaying", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "isPlaying", 0);
        }

        return false;
    }

    public static boolean doStopAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.stop();
                return true;
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doStopAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doStopAction", 0);
        }
        return false;
    }

    public static boolean doPlayAction() {
        return doPlayAction(false);
    }

    public static boolean doPlayAction(boolean keepNotification) {
        boolean isPlaying = isPlaying();
        LogUtils.LOGD(TAG, "play with notif=" + keepNotification);

        if (PlayerApplication.playerService != null) {
            try {
                if (PlayerApplication.playerService.queueGetSize() != 0) {
                    if (isPlaying) {
                        PlayerApplication.playerService.pause(keepNotification);
                    }
                    else {
                        PlayerApplication.playerService.play();
                    }
                    return true;
                }
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doPlayAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doPlayAction", 0);
        }
        return false;
    }


    public static void doPlayActionIntent() {
        final Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_TOGGLEPAUSE);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 1, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doPlayActionIntent", 0, exception);
        }
    }

    public static void doPrevActionIntent() {
        final Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PREVIOUS);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 3, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doPrevActionIntent", 0, exception);
        }
    }

    public static void doNextActionIntent() {
        final Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_NEXT);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 2, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doNextActionIntent", 0, exception);
        }
    }

    public static void doPrevAction() {
        final Intent action = new Intent(PlayerService.ACTION_CLIENT_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PREVIOUS);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 3, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doPrevAction", 0, exception);
        }
    }

    public static void doNextAction() {
        final Intent action = new Intent(PlayerService.ACTION_CLIENT_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_NEXT);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 2, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doNextAction", 0, exception);
        }
    }

    public static void doPlayActionReceiverIntent() {
        final Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PLAY);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 5, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doPlayActionIntent", 0, exception);
        }
    }

    public static void doPauseActionReceiverIntent() {
        final Intent action = new Intent(PlayerService.ACTION_NOTIFICATION_COMMAND);
        action.putExtra(PlayerService.COMMAND_KEY, PlayerService.ACTION_PAUSE);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(PlayerApplication.context, 6, action, 0);
        try {
            pendingIntent.send();
        }
        catch (final PendingIntent.CanceledException exception) {
            LogUtils.LOGException(TAG, "doPlayActionIntent", 0, exception);
        }
    }

    public static void doRepeatAction() {
        if (PlayerApplication.playerService != null) {
            try {
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
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doRepeatAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doRepeatAction", 0);
        }
    }

    public static void doShuffleAction() {
        if (PlayerApplication.playerService != null) {
            try {
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
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doShuffleAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doShuffleAction", 0);
        }
    }

    public static int getCurrentPlaylistPosition() {
        if (PlayerApplication.playerService != null) {
            try {
                return PlayerApplication.playerService.queueGetPosition();
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "getCurrentPlaylistPosition", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "getCurrentPlaylistPosition", 0);
        }

        return 0;
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
            doStopAction();
            PlayerApplication.playerManagerIndex = PlayerApplication.libraryManagerIndex;
            PlayerApplication.saveLibraryIndexes();

            try {
                PlayerApplication.playerService.queueReload();
            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doReloadServiceState", 0, remoteException);
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
            MusicConnector.doPrevAction();
        }
    };

    public static View.OnClickListener nextClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            MusicConnector.doNextAction();
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
            if (!MusicConnector.doPlayAction()) {
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
