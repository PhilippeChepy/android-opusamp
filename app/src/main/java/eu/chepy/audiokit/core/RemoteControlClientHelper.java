package eu.chepy.audiokit.core;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;

import eu.chepy.audiokit.core.broadcastreceiver.MediaButtonBroadcastReceiver;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class RemoteControlClientHelper {
    private RemoteControlClient remoteControlClient;

    @TargetApi(14)
    public void register(Context context, AudioManager audioManager) {
        if (PlayerApplication.hasICS()) {
            if (remoteControlClient == null) {
                ComponentName myEventReceiver = new ComponentName(context.getPackageName(), MediaButtonBroadcastReceiver.class.getName());
                audioManager.registerMediaButtonEventReceiver(myEventReceiver);

                Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaButtonIntent.setComponent(myEventReceiver);

                PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);
                remoteControlClient = new RemoteControlClient(mediaPendingIntent);
                remoteControlClient.setTransportControlFlags(
                        RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                                | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                );
                audioManager.registerRemoteControlClient(remoteControlClient);
            }
        }
    }

    @TargetApi(14)
    public void updateState(boolean isPlaying) {
        if (PlayerApplication.hasICS()) {
            if (remoteControlClient != null) {
                if (isPlaying) {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
                } else {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
                }
            }
        }
    }

    @TargetApi(14)
    public void stop() {
        if (PlayerApplication.hasICS()) {
            if (remoteControlClient != null) {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            }
        }
    }

    @TargetApi(14)
    public void updateMetadata(Bitmap bitmap, String title, String artist, String album, long duration) {
        if (PlayerApplication.hasICS()) {
            if (remoteControlClient != null) {
                MetadataEditor editor = remoteControlClient.editMetadata(true);

                editor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, bitmap);
                editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration);
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist);
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album);
                editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
                editor.apply();
            }
        }
    }

    /**
     * Release the remote control.
     */
    public void release() {
        remoteControlClient = null;
    }
}
