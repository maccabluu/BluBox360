package uk.co.blustudio.blubox360;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

final class StartupSound {
    private static final int SAMPLE_RATE = 22050;
    private AudioTrack track;

    StartupSound(Context context) {
    }

    void play() {
        stop();
        short[] samples = buildChime();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        track = new AudioTrack(attributes, format, samples.length * 2,
                AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            stop();
            return;
        }
        track.write(samples, 0, samples.length);
        track.setNotificationMarkerPosition(samples.length - 1);
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override public void onMarkerReached(AudioTrack audioTrack) {
                stop();
            }

            @Override public void onPeriodicNotification(AudioTrack audioTrack) {
            }
        });
        track.play();
    }

    void stop() {
        AudioTrack active = track;
        track = null;
        if (active == null) return;
        try {
            active.stop();
        } catch (IllegalStateException ignored) {
        }
        active.release();
    }

    private static short[] buildChime() {
        double duration = 1.20;
        int count = (int) (SAMPLE_RATE * duration);
        short[] data = new short[count];
        double[] frequencies = {523.25, 659.25, 783.99};
        double[] starts = {0.00, 0.18, 0.36};
        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            double value = 0.0;
            for (int note = 0; note < frequencies.length; note++) {
                double local = time - starts[note];
                if (local < 0.0) continue;
                double attack = Math.min(1.0, local / 0.025);
                double envelope = attack * Math.exp(-3.0 * local);
                value += Math.sin(2.0 * Math.PI * frequencies[note] * local) * envelope;
                value += Math.sin(2.0 * Math.PI * frequencies[note] * 2.0 * local)
                        * envelope * 0.15;
            }
            value *= 0.22;
            if (value > 1.0) value = 1.0;
            if (value < -1.0) value = -1.0;
            data[i] = (short) Math.round(value * Short.MAX_VALUE);
        }
        return data;
    }
}
