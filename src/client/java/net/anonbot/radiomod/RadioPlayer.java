package net.anonbot.radiomod;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RadioPlayer implements Runnable {

    private String streamUrl;
    private SourceDataLine dataLine;
    private volatile boolean isPlaying = false;
    private float currentVolume = 0.5f;

    public RadioPlayer(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        // --- AUDYT (PUNKT 5): USUNIĘTO System.setProperty("java.net.preferIPv4Stack", "true");

        isPlaying = true;
        try {
            URL url = new URL(streamUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            try {
                conn.getResponseCode();
            } catch (IOException e) {}

            InputStream is = new BufferedInputStream(conn.getInputStream());
            AudioInputStream in = AudioSystem.getAudioInputStream(is);
            AudioFormat baseFormat = in.getFormat();

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            AudioInputStream decodedInputStream = AudioSystem.getAudioInputStream(decodedFormat, in);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);

            dataLine = (SourceDataLine) AudioSystem.getLine(info);
            dataLine.open(decodedFormat);
            dataLine.start();

            updateVolumeControl();

            byte[] buffer = new byte[4096];
            int bytesRead;

            while (isPlaying && (bytesRead = decodedInputStream.read(buffer, 0, buffer.length)) != -1) {
                dataLine.write(buffer, 0, bytesRead);
            }

            dataLine.drain();
            dataLine.stop();
            dataLine.close();
            decodedInputStream.close();

        } catch (Exception e) {
            System.err.println("[RadioMod] Strumień zakończony lub błąd: " + e.getMessage());
        } finally {
            isPlaying = false;
        }
    }

    public void stopRadio() {
        isPlaying = false;
    }

    public void setVolume(float volume) {
        this.currentVolume = Math.max(0.0f, Math.min(volume, 1.0f));
        updateVolumeControl();
    }

    private void updateVolumeControl() {
        if (dataLine != null && dataLine.isOpen() && dataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) dataLine.getControl(FloatControl.Type.MASTER_GAIN);
            float minimum = volumeControl.getMinimum();
            float maximum = volumeControl.getMaximum();
            float db = (currentVolume == 0.0f) ? minimum : (float) (Math.log10(currentVolume) * 20.0f);
            db = Math.max(minimum, Math.min(db, maximum));
            volumeControl.setValue(db);
        }
    }

    public static String fetchCurrentSong(String streamUrl) {
        try {
            URL url = new URL(streamUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Icy-MetaData", "1");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            int metaInt = conn.getHeaderFieldInt("icy-metaint", 0);
            if (metaInt == 0) {
                return null;
            }

            InputStream is = conn.getInputStream();

            int bytesToSkip = metaInt;
            while (bytesToSkip > 0) {
                long skipped = is.skip(bytesToSkip);
                if (skipped <= 0) {
                    if (is.read() == -1) break;
                    bytesToSkip--;
                } else {
                    bytesToSkip -= skipped;
                }
            }

            int metaLen = is.read() * 16;

            // --- AUDYT (PUNKT 4): LIMIT WIELKOŚCI DANYCH METADATA
            if (metaLen <= 0 || metaLen > 4096) {
                is.close();
                return null;
            }

            byte[] metaData = new byte[metaLen];
            int bytesRead = 0;

            while (bytesRead < metaLen) {
                int read = is.read(metaData, bytesRead, metaLen - bytesRead);
                if (read == -1) break;
                bytesRead += read;
            }

            String metaString = new String(metaData, StandardCharsets.UTF_8);

            int start = metaString.indexOf("StreamTitle='");
            if (start != -1) {
                start += 13;
                int end = metaString.indexOf("';", start);
                if (end != -1) {
                    return metaString.substring(start, end).trim();
                }
            }
            is.close();
        } catch (Exception e) {
            // Ciche ignorowanie dla background workerów
        }
        return null;
    }
}