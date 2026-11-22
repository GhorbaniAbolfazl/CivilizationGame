package civilization.infrastructure.audio;

import civilization.application.port.AudioPlayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/** Dependency-free continuous ambient soundtrack synthesized at startup. */
public final class JavaSoundAudioPlayer implements AudioPlayer {
    private Clip clip;
    private double volume = 0.55;
    private boolean closed;

    @Override
    public synchronized void playLoop() {
        if (closed) {
            return;
        }
        if (clip != null && clip.isRunning()) {
            return;
        }
        try {
            if (clip == null) {
                clip = AudioSystem.getClip();
                AudioFormat format = new AudioFormat(22_050, 16, 1, true, false);
                byte[] audio = ambientWave(format, 4);
                clip.open(format, audio, 0, audio.length);
                applyVolume();
            }
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (Exception unavailableAudio) {
            // Audio hardware is optional at runtime; UI and volume state remain usable.
            close();
        }
    }

    @Override
    public synchronized void setVolume(double normalizedVolume) {
        if (!Double.isFinite(normalizedVolume)
                || normalizedVolume < 0.0 || normalizedVolume > 1.0) {
            throw new IllegalArgumentException("Volume must be between zero and one");
        }
        volume = normalizedVolume;
        applyVolume();
    }

    @Override
    public synchronized void stop() {
        if (clip != null) {
            clip.stop();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    private void applyVolume() {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float gain = volume == 0.0 ? control.getMinimum()
                : (float) Math.max(control.getMinimum(), 20.0 * Math.log10(volume));
        control.setValue(Math.min(control.getMaximum(), gain));
    }

    private static byte[] ambientWave(AudioFormat format, int seconds) {
        int frames = Math.toIntExact((long) format.getSampleRate() * seconds);
        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double time = i / format.getSampleRate();
            double fade = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / frames);
            double sample = 0.10 * fade * (
                    Math.sin(2.0 * Math.PI * 110.0 * time)
                    + 0.55 * Math.sin(2.0 * Math.PI * 164.81 * time)
                    + 0.35 * Math.sin(2.0 * Math.PI * 220.0 * time));
            short pcm = (short) Math.round(sample * Short.MAX_VALUE);
            data[i * 2] = (byte) (pcm & 0xff);
            data[i * 2 + 1] = (byte) ((pcm >>> 8) & 0xff);
        }
        return data;
    }
}
