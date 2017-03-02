package net.opusapp.player.core.service.event;

public class PlaylistChangedEvent {

    private int mProviderId;

    private int mPlaylistId;

    private int mPlaylistLength;


    public PlaylistChangedEvent(int providerId, int playlistId, int playlistLength) {
        mProviderId = providerId;
        mPlaylistId = playlistId;
        mPlaylistLength = playlistLength;
    }

    public int getProviderId() {
        return mProviderId;
    }

    public int getPlaylistId() {
        return mPlaylistId;
    }

    public int getPlaylistLength() {
        return mPlaylistLength;
    }
}
