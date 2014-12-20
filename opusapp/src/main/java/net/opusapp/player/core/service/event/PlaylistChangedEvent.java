package net.opusapp.player.core.service.event;

public class PlaylistChangedEvent {

    private int mProviderId;

    private int mPlaylistId;


    public PlaylistChangedEvent(int providerId, int playlistId) {
        mProviderId = providerId;
        mPlaylistId = playlistId;
    }

    public int getProviderId() {
        return mProviderId;
    }

    public int getPlaylistId() {
        return mPlaylistId;
    }
}
