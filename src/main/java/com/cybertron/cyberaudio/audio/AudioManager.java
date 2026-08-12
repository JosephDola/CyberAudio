package com.cybertron.cyberaudio.audio;

import com.cybertron.cyberaudio.CyberAudio;
import com.cybertron.cyberaudio.resolver.DirectAudioResolver;
import com.cybertron.cyberaudio.resolver.ResolvedMedia;
import com.cybertron.cyberaudio.util.PerformanceMonitor;

import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioManager implements AutoCloseable {
    private static final int IO_BUFFER = 32 * 1024;
    private final DirectAudioResolver resolver = new DirectAudioResolver();
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CyberAudio-Playback");
        t.setDaemon(true);
        return t;
    });
    private final Deque<String> queue = new ArrayDeque<>();
    private final PerformanceMonitor performance = new PerformanceMonitor();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final Object pauseLock = new Object();
    private volatile PlaybackState state = PlaybackState.IDLE;
    private volatile String currentUrl = "";
    private volatile String currentTitle = "Nothing playing";
    private volatile String lastError = "";
    private volatile float volume = 0.75f;
    private volatile boolean paused;
    private volatile SourceDataLine currentLine;
    private volatile InputStream currentNetworkStream;

    public synchronized CompletableFuture<Void> play(String url) {
        stop();
        currentUrl = url == null ? "" : url.trim();
        if (currentUrl.isBlank()) {
            state = PlaybackState.FAILED;
            lastError = "Enter a direct HTTP/HTTPS audio URL.";
            return CompletableFuture.failedFuture(new IllegalArgumentException(lastError));
        }

        performance.reset();
        stopRequested.set(false);
        paused = false;
        state = PlaybackState.RESOLVING;
        lastError = "";
        long submitted = System.nanoTime();

        return resolver.resolve(currentUrl).thenAcceptAsync(media -> {
            performance.setResolveLatencyMs(Duration.ofNanos(System.nanoTime() - submitted).toMillis());
            currentTitle = media.title();
            state = PlaybackState.BUFFERING;
            try {
                stream(media, submitted);
            } catch (Exception e) {
                if (!stopRequested.get()) {
                    state = PlaybackState.FAILED;
                    lastError = readableError(e);
                    CyberAudio.LOGGER.error("CyberAudio playback failed for {}", currentUrl, e);
                }
            } finally {
                cleanupPlaybackResources();
                if (!stopRequested.get() && state != PlaybackState.FAILED) {
                    state = PlaybackState.STOPPED;
                    playNextIfQueued();
                }
            }
        }, audioExecutor);
    }

    private void stream(ResolvedMedia media, long submittedNanos) throws Exception {
        URLConnection connection = media.sourceUri().toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "CyberAudio/0.2.1");
        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
            int status = http.getResponseCode();
            if (status >= 400) throw new IOException("HTTP " + status);
        }

        CountingInputStream counting = new CountingInputStream(connection.getInputStream(), performance);
        currentNetworkStream = counting;
        try (BufferedInputStream buffered = new BufferedInputStream(counting, IO_BUFFER);
             AudioInputStream encoded = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat source = encoded.getFormat();
            AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(),
                    16,
                    source.getChannels(),
                    source.getChannels() * 2,
                    source.getSampleRate(),
                    false
            );
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcm));
                currentLine = line;
                line.open(pcm, IO_BUFFER * 2);
                applyVolume(line);
                line.start();
                state = PlaybackState.PLAYING;
                performance.setStartLatencyMs(Duration.ofNanos(System.nanoTime() - submittedNanos).toMillis());

                byte[] buffer = new byte[IO_BUFFER];
                while (!stopRequested.get()) {
                    waitIfPaused(line);
                    if (stopRequested.get()) break;
                    int read = decoded.read(buffer);
                    if (read < 0) break;
                    int offset = 0;
                    while (offset < read && !stopRequested.get()) {
                        offset += line.write(buffer, offset, read - offset);
                    }
                }
                if (!stopRequested.get()) line.drain();
            }
        }
    }

    private void waitIfPaused(SourceDataLine line) throws InterruptedException {
        synchronized (pauseLock) {
            while (paused && !stopRequested.get()) {
                state = PlaybackState.PAUSED;
                line.stop();
                pauseLock.wait();
            }
            if (!stopRequested.get() && !paused) {
                line.start();
                state = PlaybackState.PLAYING;
            }
        }
    }

    public void togglePause() {
        synchronized (pauseLock) {
            if (state == PlaybackState.PLAYING) {
                paused = true;
                if (currentLine != null) currentLine.stop();
                state = PlaybackState.PAUSED;
            } else if (state == PlaybackState.PAUSED) {
                paused = false;
                pauseLock.notifyAll();
            }
        }
    }

    public synchronized void stop() {
        stopRequested.set(true);
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        cleanupPlaybackResources();
        if (state != PlaybackState.IDLE) state = PlaybackState.STOPPED;
    }

    public synchronized void enqueue(String url) {
        if (url != null && !url.isBlank()) queue.addLast(url.trim());
    }

    private synchronized void playNextIfQueued() {
        String next = queue.pollFirst();
        if (next != null) play(next);
    }

    public synchronized int queueSize() {
        return queue.size();
    }

    public synchronized void clearQueue() {
        queue.clear();
    }

    public void setVolume(float value) {
        volume = Math.clamp(value, 0.0f, 1.0f);
        if (currentLine != null) applyVolume(currentLine);
    }

    private void applyVolume(SourceDataLine line) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        float gain = volume <= 0.0001f ? control.getMinimum() : (float) (20.0 * Math.log10(volume));
        control.setValue(Math.clamp(gain, control.getMinimum(), control.getMaximum()));
    }

    private void cleanupPlaybackResources() {
        SourceDataLine line = currentLine;
        currentLine = null;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.flush(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
        }
        InputStream stream = currentNetworkStream;
        currentNetworkStream = null;
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) {}
        }
    }

    private static String readableError(Exception error) {
        String message = error.getMessage();
        if (error instanceof UnsupportedAudioFileException) return "Unsupported audio format.";
        if (error instanceof LineUnavailableException) return "Audio output device is unavailable.";
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public PlaybackState state() { return state; }
    public String currentUrl() { return currentUrl; }
    public String currentTitle() { return currentTitle; }
    public String lastError() { return lastError; }
    public float volume() { return volume; }
    public PerformanceMonitor performance() { return performance; }

    @Override
    public void close() {
        stop();
        audioExecutor.shutdownNow();
    }

    private static final class CountingInputStream extends FilterInputStream {
        private final PerformanceMonitor performance;

        private CountingInputStream(InputStream in, PerformanceMonitor performance) {
            super(in);
            this.performance = performance;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) performance.addDownloadedBytes(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) performance.addDownloadedBytes(count);
            return count;
        }
    }
}
