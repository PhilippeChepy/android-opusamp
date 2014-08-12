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
package eu.chepy.audiokit.ui.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.RemoteException;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.Metadata;
import eu.chepy.audiokit.ui.activities.LibraryMainActivity;
import eu.chepy.audiokit.ui.adapter.ux.MetadataListAdapter;
import eu.chepy.audiokit.utils.LogUtils;

public class MusicConnector {
	
	public static final String TAG = "MusicConnector";







    public static boolean show_hidden = false;


    public static int album_artists_sort_order = AbstractMediaProvider.ALBUM_ARTIST_NAME;

    public static int albums_sort_order = AbstractMediaProvider.ALBUM_NAME;

    public static int artists_sort_order = AbstractMediaProvider.ARTIST_NAME;

    public static int genres_sort_order = AbstractMediaProvider.GENRE_NAME;

    public static int playlists_sort_order = AbstractMediaProvider.PLAYLIST_NAME;

    public static int songs_sort_order = AbstractMediaProvider.SONG_TRACK;

    public static int storage_sort_order = AbstractMediaProvider.STORAGE_DISPLAY_NAME;



    public static int details_songs_sort_order = AbstractMediaProvider.SONG_TRACK;

    public static int details_albums_sort_order = AbstractMediaProvider.ALBUM_NAME;


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

    public static void doPrevAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.prev();
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doPrevAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doPrevAction", 0);
        }
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

    public static void doNextAction() {
        if (PlayerApplication.playerService != null) {
            try {
                PlayerApplication.playerService.next();
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doNextAction", 0, remoteException);
            }
        }
        else {
            LogUtils.LOGService(TAG, "doNextAction", 0);
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
    public static boolean doContextActionPlay(AbstractMediaProvider.ContentType sourceType, String sourceId, int sortOrder, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(sourceType, sourceId, sortOrder, position, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionPlayNext(AbstractMediaProvider.ContentType sourceType, String sourceId, int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionAddToQueue(AbstractMediaProvider.ContentType sourceType, String sourceId, int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(null, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionAddToPlaylist(Activity hostActivity, final AbstractMediaProvider.ContentType sourceType, final String sourceId, final int sortOrder) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(String playlistId) {
                LogUtils.LOGD(TAG, "trying to add to " + playlistId);
                mediaProvider.playlistAdd(playlistId, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
            }
        });
        return true;
    }

    public static boolean doContextActionToggleVisibility(AbstractMediaProvider.ContentType sourceType, String sourceId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        mediaProvider.setProperty(sourceType, sourceId, AbstractMediaProvider.ContentProperty.CONTENT_VISIBILITY_TOGGLE, null, null);

        return true;
    }

    public static boolean doContextActionMediaRemoveFromQueue(String playlistId, int position) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        mediaProvider.playlistRemove(playlistId, position);
        return true;
    }

    public static boolean doContextActionPlaylistClear(String playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        mediaProvider.playlistClear(playlistId);
        return true;
    }

    public static boolean doContextActionDetail(Activity hostActivity, AbstractMediaProvider.ContentType contentType, String contentId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        int titleResId = R.string.dialog_title_media_properties;
        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                titleResId = R.string.dialog_title_album_properties;

                break;
            case CONTENT_TYPE_MEDIA:
                break;
            default:
                return false;
        }

        final Object metadataList = mediaProvider.getProperty(contentType, contentId, AbstractMediaProvider.ContentProperty.CONTENT_METADATA_LIST);
        final MetadataListAdapter adapter = new MetadataListAdapter(hostActivity, (ArrayList<Metadata>)metadataList);

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
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mediaProvider.playlistDelete(playlistId);
                if (hostActivity instanceof LibraryMainActivity) {
                    ((LibraryMainActivity)hostActivity).doRefresh();
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
                .setTitle(R.string.alert_title_playlist_delete)
                .setMessage(R.string.alert_playlist_delete)
                .setView(textView)
                .setPositiveButton(android.R.string.ok, newPlaylistPositiveOnClickListener)
                .setNegativeButton(android.R.string.cancel, newPlaylistNegativeOnClickListener)
                .show();
        return true;
    }

    public static void doManageControlIntent(Intent intent) {
        final String source = intent.getAction();
        final String action = intent.getStringExtra(PlayerService.COMMAND_KEY);

        boolean isRemoteControl = source != null &&
                (source.equals(PlayerService.ACTION_NOTIFICATION_COMMAND) ||
                source.equals(PlayerService.ACTION_APPWIDGET_COMMAND));

        if (action != null && isRemoteControl) {
            if (action.equals(PlayerService.ACTION_PREVIOUS)) {
                MusicConnector.doPrevAction();
            }
            else if (action.equals(PlayerService.ACTION_NEXT)) {
                MusicConnector.doNextAction();
            }
            else if (action.equals(PlayerService.ACTION_STOP)) {
                MusicConnector.doStopAction();
            }
            else if (action.equals(PlayerService.ACTION_TOGGLEPAUSE)) {
                boolean isNotificationControl = source.equals(PlayerService.ACTION_NOTIFICATION_COMMAND);
                MusicConnector.doPlayAction(isNotificationControl);
            }
        }
    }





    /*
        Telephony manager
     */
    private static boolean pausedByCallManager = false;

	private static void doCallPausePlayback() {
		if ((!pausedByCallManager) && (PlayerApplication.playerService != null)) {
			try {
				if (PlayerApplication.playerService.isPlaying()) {
					pausedByCallManager = true;
                    PlayerApplication.playerService.pause(true);
				}
			} catch (final RemoteException remoteException) {
				LogUtils.LOGException(TAG, "doCallPausePlayback", 0, remoteException);
			}
		}
		else {
			LogUtils.LOGService(TAG, "doCallPausePlayback", 0);
		}
	}
	
	private static void doCallResumePlayback() {
		if (pausedByCallManager && (PlayerApplication.playerService != null)) {
			try {
				pausedByCallManager = false;
                PlayerApplication.playerService.pause(true);
			} catch (final RemoteException remoteException) {
				LogUtils.LOGException(TAG, "doCallResumePlayback", 0, remoteException);
			}
		}
		else {
			LogUtils.LOGService(TAG, "doCallResumePlayback", 0);
		}
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

	public static void doCallManageIdle() {
		doCallPausePlayback();
	}

	public static void doCallManageOffHook() {
		doCallPausePlayback();
	}

	public static void doCallManageRinging() {
		doCallResumePlayback();
	}



    /*

     */
    interface PlaylistManagementRunnable {
        public void run(String playlistId);
    }



    protected static void showPlaylistManagementDialog(final Activity hostActivity, final PlaylistManagementRunnable runnable) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        final Cursor playlistCursor = mediaProvider.buildCursor(
                AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST,
                new int[] {
                        AbstractMediaProvider.PLAYLIST_ID,
                        AbstractMediaProvider.PLAYLIST_NAME
                },
                new int[] {
                        AbstractMediaProvider.PLAYLIST_ID
                },
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
                final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

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
                                    ((LibraryMainActivity)hostActivity).doRefresh();
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
                    .setTitle(R.string.mi_add_to_playlist)
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
                        alertDialog.setTitle(R.string.alert_title_playlist_is_empty);
                        alertDialog.setMessage(currentActivity.getString(R.string.alert_playlist_is_empty));
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
