package uk.co.blustudio.blubox360;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;

final class StartupSound {
    private static final int SAMPLE_RATE = 44100;
    private static final double DURATION_SECONDS = 1.45;
    private static final Object LOCK = new Object();
    private static MediaPlayer activePlayer;

    private final Context context;

    StartupSound(Context context) {
        this.context = context.getApplicationContext();
    }

    void play() {
        synchronized (LOCK) {
            stopLocked();
            try {
                File sound = ensureSoundFile();
                MediaPlayer player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                player.setDataSource(sound.getAbsolutePath());
                player.setVolume(1f, 1f);
                player.setOnCompletionListener(StartupSound::releasePlayer);
                player.setOnErrorListener((mediaPlayer, what, extra) -> {
                    releasePlayer(mediaPlayer);
                    return true;
                });
                player.prepare();
                activePlayer = player;
                player.start();
            } catch (Throwable ignored) {
                stopLocked();
            }
        }
    }

    void stop() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private File ensureSoundFile() throws Exception {
        File file = new File(context.getCacheDir(), "blubox_startup_chime_v2.wav");
        if (file.isFile() && file.length() > 4096) return file;

        short[] samples = buildChime();
        byte[] wav = new byte[44 + samples.length * 2];
        writeAscii(wav, 0, "RIFF");
        writeIntLE(wav, 4, 36 + samples.length * 2);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeIntLE(wav, 16, 16);
        writeShortLE(wav, 20, 1);
        writeShortLE(wav, 22, 1);
        writeIntLE(wav, 24, SAMPLE_RATE);
        writeIntLE(wav, 28, SAMPLE_RATE * 2);
        writeShortLE(wav, 32, 2);
        writeShortLE(wav, 34, 16);
        writeAscii(wav, 36, "data");
        writeIntLE(wav, 40, samples.length * 2);
        int offset = 44;
        for (short sample : samples) {
            writeShortLE(wav, offset, sample);
            offset += 2;
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(wav);
            out.flush();
        }
        return file;
    }

    private static short[] buildChime() {
        int count = (int) Math.round(SAMPLE_RATE * DURATION_SECONDS);
        short[] data = new short[count];
        double[] frequencies = {392.00, 523.25, 659.25, 783.99};
        double[] starts = {0.00, 0.15, 0.31, 0.49};
        double[] gains = {0.62, 0.67, 0.72, 0.55};

        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            double value = 0.0;
            for (int note = 0; note < frequencies.length; note++) {
                double local = time - starts[note];
                if (local < 0.0) continue;
                double attack = Math.min(1.0, local / 0.018);
                double decay = Math.exp(-2.75 * local);
                double envelope = attack * decay * gains[note];
                double phase = 2.0 * Math.PI * frequencies[note] * local;
                value += Math.sin(phase) * envelope;
                value += Math.sin(phase * 2.0) * envelope * 0.14;
                value += Math.sin(phase * 0.5) * envelope * 0.08;
            }
            if (time > 0.78) {
                double tail = time - 0.78;
                value += Math.sin(2.0 * Math.PI * 261.63 * tail)
                        * Math.exp(-4.2 * tail) * 0.16;
            }
            value = Math.tanh(value * 0.88) * 0.92;
            data[i] = (short) Math.round(value * Short.MAX_VALUE);
        }
        return data;
    }

    private static void releasePlayer(MediaPlayer player) {
        synchronized (LOCK) {
            if (activePlayer == player) activePlayer = null;
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void stopLocked() {
        MediaPlayer player = activePlayer;
        activePlayer = null;
        if (player == null) return;
        try {
            player.stop();
        } catch (Throwable ignored) {
        }
        try {
            player.release();
        } catch (Throwable ignored) {
        }
    }

    private static void writeAscii(byte[] data, int offset, String text) {
        for (int i = 0; i < text.length(); i++) data[offset + i] = (byte) text.charAt(i);
    }

    private static void writeShortLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static void writeIntLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }
}
