package com.cybertron.cyberaudio.util;

import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMonitor {
    private final AtomicLong downloadedBytes = new AtomicLong();
    private final AtomicLong underruns = new AtomicLong();
    private volatile long resolveLatencyMs;
    private volatile long startLatencyMs;
    public void reset() { downloadedBytes.set(0); underruns.set(0); resolveLatencyMs = 0; startLatencyMs = 0; }
    public void addDownloadedBytes(long count) { downloadedBytes.addAndGet(Math.max(0, count)); }
    public long downloadedBytes() { return downloadedBytes.get(); }
    public void markUnderrun() { underruns.incrementAndGet(); }
    public long underruns() { return underruns.get(); }
    public long resolveLatencyMs() { return resolveLatencyMs; }
    public void setResolveLatencyMs(long value) { resolveLatencyMs = Math.max(0, value); }
    public long startLatencyMs() { return startLatencyMs; }
    public void setStartLatencyMs(long value) { startLatencyMs = Math.max(0, value); }
}
