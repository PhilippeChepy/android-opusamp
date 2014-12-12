package net.opusapp.player.core.service.output;

public class ChromecastOutput implements PlaybackOutput {


    public ChromecastOutput() {
        /*
        new MediaInfo.Builder()
                .setMediaTracks()

        final MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, );
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, );
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, );
        mediaMetadata.putString(MediaMetadata.KEY_ARTIST, );
        mediaMetadata.putString(MediaMetadata.KEY_COMPOSER, );
        mediaMetadata.putString(MediaMetadata.KEY_TRACK_NUMBER, );
        mediaMetadata.putString(MediaMetadata.KEY_DISC_NUMBER, );
        mediaMetadata.clearImages();
        mediaMetadata.addImage();
        mediaMetadata.putDate(MediaMetadata.KEY_RELEASE_DATE, );
        */
    }

    protected void initChromecastSettings() {

    }

    protected void initChormecast() {

    }


    @Override
    public int getMinSamplingRate() {
        return 0;
    }

    @Override
    public int getMaxSamplingRate() {
        return 0;
    }

    @Override
    public int getCurrentSamplingRate() {
        return 0;
    }

    @Override
    public boolean setSamplingRate(int format) {
        return false;
    }

    @Override
    public boolean sampleFormatIsSupported(int format) {
        return false;
    }

    @Override
    public boolean setSampleFormat(int format) {
        return false;
    }

    @Override
    public void applySettings() {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public int write(byte[] data, int offset, int length) {
        return 0;
    }

    @Override
    public int write(short[] data, int offset, int length) {
        return 0;
    }

    @Override
    public int write(float[] data, int offset, int length) {
        return 0;
    }
}
