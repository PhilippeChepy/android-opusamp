package net.opusapp.player.core.service.output;

public interface PlaybackOutput {


    public static final int SAMPLE_INT8 = 1;

    public static final int SAMPLE_INT16 = 2;

    public static final int SAMPLE_FLOAT_32 = 3;




    public int getMinSamplingRate();

    public int getMaxSamplingRate();

    public int getCurrentSamplingRate();

    public boolean setSamplingRate(int format);

    public boolean sampleFormatIsSupported(int format);

    public boolean setSampleFormat(int format);

    public void applySettings();


    public void start();

    public void stop();



    public int write(byte[] data, int offset, int length);

    public int write(short[] data, int offset, int length);

    public int write(float[] data, int offset, int length);
}
