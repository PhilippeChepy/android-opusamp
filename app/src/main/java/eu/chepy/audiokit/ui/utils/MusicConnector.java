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
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.IPlayerService;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.Metadata;
import eu.chepy.audiokit.ui.activities.LibraryMainActivity;
import eu.chepy.audiokit.ui.adapter.ux.MetadataListAdapter;
import eu.chepy.audiokit.utils.LogUtils;

public class MusicConnector {
	
	public static final String TAG = "MusicConnector";



	public static IPlayerService playerService = null;



    public static boolean show_hidden = false;


    public static int album_artists_sort_order = AbstractMediaProvider.ALBUM_ARTIST_NAME;

    public static int albums_sort_order = AbstractMediaProvider.ALBUM_NAME;

    public static int artists_sort_order = AbstractMediaProvider.ARTIST_NAME;

    public static int genres_sort_order = AbstractMediaProvider.GENRE_NAME;

    public static int playlists_sort_order = AbstractMediaProvider.PLAYLIST_NAME;

    public static int songs_sort_order = AbstractMediaProvider.SONG_TRACK;

    public static int storage_sort_order = AbstractMediaProvider.STORAGE_DISPLAY_NAME;



    /*
        General service control
     */
    public static boolean isPlaying() {
        if (playerService != null) {
            try {
                return playerService.isPlaying();
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
        Log.d(TAG, "doPrevAction()");

        if (playerService != null) {
            try {
                playerService.prev();
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
        Log.d(TAG, "doStopAction()");

        if (playerService != null) {
            try {
                playerService.stop();
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
        Log.d(TAG, "doPlayAction()");

        boolean isPlaying = isPlaying();

        if (playerService != null) {
            try {
                if (playerService.queueGetSize() != 0) {
                    if (isPlaying) {
                        playerService.pause(keepNotification);
                    }
                    else {
                        playerService.play();
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
        Log.d(TAG, "doNextAction()");

        if (playerService != null) {
            try {
                playerService.next();
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
        Log.d(TAG, "doRepeatAction()");

        if (playerService != null) {
            try {
                int repeatMode = playerService.getRepeatMode();

                switch (repeatMode) {
                    case PlayerService.REPEAT_NONE:
                        playerService.setRepeatMode(PlayerService.REPEAT_CURRENT);
                        break;
                    case PlayerService.REPEAT_CURRENT:
                        playerService.setRepeatMode(PlayerService.REPEAT_ALL);
                        break;
                    case PlayerService.REPEAT_ALL:
                        playerService.setRepeatMode(PlayerService.REPEAT_NONE);
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
        Log.d(TAG, "doShuffleAction()");

        if (playerService != null) {
            try {
                int shuffleMode = playerService.getShuffleMode();

                switch (shuffleMode) {
                    case PlayerService.SHUFFLE_AUTO:
                        playerService.setShuffleMode(PlayerService.SHUFFLE_NONE);
                        break;
                    case PlayerService.SHUFFLE_NONE:
                        playerService.setShuffleMode(PlayerService.SHUFFLE_AUTO);
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
        Log.d(TAG, "getCurrentPlaylistPosition()");

        if (playerService != null) {
            try {
                return playerService.queueGetPosition();
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
                Log.w(TAG, "trying to add to " + playlistId);
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

        Object metadataList = mediaProvider.getProperty(contentType, contentId, AbstractMediaProvider.ContentProperty.CONTENT_METADATA_LIST);

        MetadataListAdapter adapter = new MetadataListAdapter(hostActivity, (ArrayList<Metadata>)metadataList);


        new AlertDialog.Builder(hostActivity)
                .setTitle(R.string.dialog_title_media_properties)
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

    /*
    public static boolean doContextActionMediaPlay(int mediaId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_DEFAULT, mediaId, songs_sort_order, position);
    }

    public static boolean doContextActionMediaPlayNext(int mediaId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_SINGLE_MEDIA, mediaId, songs_sort_order);
    }

    public static boolean doContextActionMediaAddToQueue(int mediaId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_SINGLE_MEDIA, mediaId, songs_sort_order);
    }

    public static boolean doContextActionMediaAddToPlaylist(Activity hostActivity, final int mediaId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(int playlistId) {
                Log.w(TAG, "trying to add to " + playlistId);
                mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_SINGLE_MEDIA, mediaId, songs_sort_order);
            }
        });
        return true;
    }

    public static boolean doContextActionMediaToggleVisibility(int mediaId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
        return mediaProvider.toggleMediaVisibility(mediaId);
    }

    public static boolean doContextActionMediaRemoveFromQueue(int playlistId, int position) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        mediaProvider.playlistRemove(playlistId, position);
        return true;
    }

    public static boolean doContextActionPlaylistClear(int playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        mediaProvider.playlistClear(playlistId);
        return true;
    }



    public static boolean doContextActionPlaylistPlay(int playlistId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_PLAYLIST, playlistId, songs_sort_order, position);
    }

    public static boolean doContextActionPlaylistPlayNext(int playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_PLAYLIST, playlistId, songs_sort_order);
    }

    public static boolean doContextActionPlaylistAddToQueue(int playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_PLAYLIST, playlistId, songs_sort_order);
    }

    public static boolean doContextActionPlaylistToggleVisibility(int playlistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.togglePlaylistVisibility(playlistId);
    }
    */

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
/*


    public static boolean doContextActionGenrePlay(int genreId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_GENRE, genreId, songs_sort_order, position);
    }

    public static boolean doContextActionGenrePlayNext(int genreId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_GENRE, genreId, songs_sort_order);
    }

    public static boolean doContextActionGenreAddToQueue(int genreId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_GENRE, genreId, songs_sort_order);
    }

    public static boolean doContextActionGenreAddToPlaylist(final Activity hostActivity, final int genreId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
                public void run(int playlistId) {
                    mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_GENRE, genreId, songs_sort_order);
                }
            });
        return true;
    }

    public static boolean doContextActionGenreToggleVisibility(int genreId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.toggleGenreVisibility(genreId);
    }



    public static boolean doContextActionArtistPlay(int artistId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_ARTIST, artistId, songs_sort_order, position);
    }

    public static boolean doContextActionArtistPlayNext(int artistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_ARTIST, artistId, songs_sort_order);
    }

    public static boolean doContextActionArtistAddToQueue(int artistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_ARTIST, artistId, songs_sort_order);
    }

    public static boolean doContextActionArtistAddToPlaylist(Activity hostActivity, final int artistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(int playlistId) {
                mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_ARTIST, artistId, songs_sort_order);
            }
        });
        return true;
    }

    public static boolean doContextActionArtistToggleVisibility(int artistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.toggleArtistVisibility(artistId);
    }



    public static boolean doContextActionAlbumPlay(int albumId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_ALBUM, albumId, songs_sort_order, position);
    }

    public static boolean doContextActionAlbumPlayNext(int albumId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_ALBUM, albumId, songs_sort_order);
    }

    public static boolean doContextActionAlbumAddToQueue(int albumId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_ALBUM, albumId, songs_sort_order);
    }

    public static boolean doContextActionAlbumAddToPlaylist(Activity hostActivity, final int albumId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(int playlistId) {
                mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_ALBUM, albumId, songs_sort_order);
            }
        });
        return true;
    }

    public static boolean doContextActionAlbumToggleVisibility(int albumId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.toggleAlbumVisibility(albumId);
    }



    public static boolean doContextActionAlbumArtistPlay(int albumArtistId, int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, songs_sort_order, position);
    }

    public static boolean doContextActionAlbumArtistPlayNext(int albumArtistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playNext(AbstractMediaProvider.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, songs_sort_order);
    }

    public static boolean doContextActionAlbumArtistAddToQueue(int albumArtistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.playlistAdd(CURRENT_PLAYLIST_ID, AbstractMediaProvider.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, songs_sort_order);
    }

    public static boolean doContextActionAlbumArtistAddToPlaylist(Activity hostActivity, final int albumArtistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(int playlistId) {
                mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, songs_sort_order);
            }
        });
        return true;
    }

    public static boolean doContextActionAlbumArtistToggleVisibility(int albumArtistId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.toggleAlbumArtistVisibility(albumArtistId);
    }
*/


/*    public static boolean doContextActionStoragePlay(final File fileList[], int position) {
        doPrepareProviderSwitch();

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        return mediaProvider.play(AbstractMediaProvider.ContentType.CONTENT_TYPE_DEFAULT, fileList, position);
    }

    public static boolean doContextActionStoragePlayNext(File file) {
        // CONTENT_TYPE_SINGLE_MEDIA because adding only one song to the queue
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        final File fileList[] = new File[1];
        fileList[0] = file;
        return mediaProvider.playNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, fileList);
    }

    public static boolean doContextActionStorageAddToQueue(File file) {
        // CONTENT_TYPE_SINGLE_MEDIA because adding only one song to the queue
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        final File fileList[] = new File[1];
        fileList[0] = file;
        return mediaProvider.playlistAddToQueue(fileList);
    }
*/

/*
    public static boolean doContextActionPlaylistItemAddToPlaylist(Activity hostActivity, final int mediaId) {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.playerManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        showPlaylistManagementDialog(hostActivity, new PlaylistManagementRunnable() {
            public void run(int playlistId) {
                mediaProvider.playlistAdd(playlistId, AbstractMediaProvider.CONTENT_TYPE_SINGLE_MEDIA, mediaId, songs_sort_order);
            }
        });
        return true;
    }
    */


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
		if ((!pausedByCallManager) && (playerService != null)) {
			try {
				if (playerService.isPlaying()) {
					pausedByCallManager = true;
					playerService.pause(true);
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
		if (pausedByCallManager && (playerService != null)) {
			try {
				pausedByCallManager = false;
				playerService.pause(true);
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
                playerService.queueReload();

            } catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "doReloadServiceState", 0, remoteException);
            }
        }
    }

	public static void doCallManageIdle() {
		Log.d(TAG, "doCallManageIdle()");
		doCallPausePlayback();
	}

	public static void doCallManageOffHook() {
		Log.d(TAG, "doCallManageOffHook()");
		doCallPausePlayback();
	}

	public static void doCallManageRinging() {
		Log.d(TAG, "doCallManageRinging()");
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
