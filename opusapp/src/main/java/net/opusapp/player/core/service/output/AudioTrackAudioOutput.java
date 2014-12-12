package net.opusapp.player.core.service.output;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import net.opusapp.player.ui.utils.PlayerApplication;

public class AudioTrackAudioOutput implements PlaybackOutput {



    private AudioTrack mAudioTrack;

    private int mStreamType;

    private int mMinSamplingRate;

    private int mMaxSamplingRate;

    private int mNativeSamplingRate;

    private int mCurrentSamplingRate;

    private int mCurrentChannelConfig;

    private int mAudioFormat;

    private int mBufferSize;



    public AudioTrackAudioOutput() {
        mStreamType = AudioManager.STREAM_MUSIC;

        initAudioTrackSettings();
        initAudioTrack();
    }


    protected void initAudioTrackSettings() {
        mNativeSamplingRate = AudioTrack.getNativeOutputSampleRate(mStreamType);
        mMinSamplingRate = 1;
        mMaxSamplingRate = mNativeSamplingRate * 2;

        mCurrentSamplingRate = mNativeSamplingRate;
        mCurrentChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    }

    protected void initAudioTrack() {
        mBufferSize = AudioTrack.getMinBufferSize(mCurrentSamplingRate, mCurrentChannelConfig, mAudioFormat);
        mAudioTrack = new AudioTrack(mStreamType, mCurrentSamplingRate, mCurrentChannelConfig, mAudioFormat, mBufferSize, AudioTrack.MODE_STREAM);
    }


    @Override
    public int getMinSamplingRate() {
        return mMinSamplingRate;
    }

    @Override
    public int getMaxSamplingRate() {
        return mMaxSamplingRate;
    }

    @Override
    public int getCurrentSamplingRate() {
        return mCurrentSamplingRate;
    }

    @Override
    public boolean setSamplingRate(int samplingRate) {
        if (samplingRate < mMinSamplingRate || samplingRate > mMaxSamplingRate) {
            return false;
        }

        mCurrentSamplingRate = samplingRate;
        return true;
    }

    @Override
    public boolean sampleFormatIsSupported(int format) {
        switch (format) {
            case SAMPLE_INT8:
            case SAMPLE_INT16:
                return true;
            case SAMPLE_FLOAT_32:
                return PlayerApplication.hasLollipop();
        }
        return false;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean setSampleFormat(int format) {
        if (!sampleFormatIsSupported(format)) {
            return false;
        }

        switch (format) {
            case SAMPLE_INT8:
                mAudioFormat = AudioFormat.ENCODING_PCM_8BIT;
                break;
            case SAMPLE_INT16:
                mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
                break;
            case SAMPLE_FLOAT_32:
                mAudioFormat = AudioFormat.ENCODING_PCM_FLOAT;
                break;
        }

        return true;
    }

    // TODO: channel settings

    @Override
    public void applySettings() {
        initAudioTrack();
    }

    @Override
    public void start() {
        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.play();
        }
    }

    @Override
    public void stop() {
        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
            mAudioTrack.stop();
        }
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        if (mAudioFormat == AudioFormat.ENCODING_PCM_8BIT) {
            return mAudioTrack.write(data, offset, length);
        }

        return -1;
    }

    @Override
    public int write(short[] data, int offset, int length) {
        if (mAudioFormat == AudioFormat.ENCODING_PCM_16BIT) {
            mAudioTrack.write(data, offset, length);
        }

        return -1;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public int write(float[] data, int offset, int length) {
        if (mAudioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            mAudioTrack.write(data, offset, length, AudioTrack.WRITE_BLOCKING);
        }

        return -1;
    }
}
