package com.tfassbender.loreweave.watch.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tracks the timestamp of the last poll on {@code /api/validation} and fires
 * a callback when no poll has arrived within the idle threshold. A startup
 * grace window suppresses the timeout long enough for the browser to launch
 * and connect.
 *
 * <p>Any thread may call {@link #recordPoll()} concurrently. The shutdown
 * callback runs at most once.
 */
public final class IdleShutdown {

    static final long DEFAULT_THRESHOLD_MILLIS = 10_000L;
    static final long DEFAULT_GRACE_MILLIS = 30_000L;
    private static final long TICK_MILLIS = 1_000L;

    private volatile long thresholdMillis;
    private volatile long graceMillis;
    private volatile boolean enabled = true;
    private final long startedAt;
    private final LongSupplier clock;
    private final Runnable onIdle;
    private final ScheduledExecutorService scheduler;
    private volatile long lastPollMillis;
    private volatile boolean fired;

    public IdleShutdown(Runnable onIdle) {
        this(DEFAULT_THRESHOLD_MILLIS, DEFAULT_GRACE_MILLIS, System::currentTimeMillis, onIdle);
    }

    IdleShutdown(long thresholdMillis, long graceMillis, LongSupplier clock, Runnable onIdle) {
        this.thresholdMillis = thresholdMillis;
        this.graceMillis = graceMillis;
        this.clock = clock;
        this.onIdle = onIdle;
        this.startedAt = clock.getAsLong();
        this.lastPollMillis = startedAt;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lwwatch-idle-shutdown");
            t.setDaemon(true);
            return t;
        });
    }

    /** Hot-reloadable setter. Existing tick loop picks up the new values. */
    public void update(boolean enabled, long thresholdMillis, long graceMillis) {
        this.enabled = enabled;
        this.thresholdMillis = thresholdMillis;
        this.graceMillis = graceMillis;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Visible for tests — fires a single tick synchronously. */
    void tickNow() {
        tick();
    }

    public void recordPoll() {
        lastPollMillis = clock.getAsLong();
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void tick() {
        if (fired || !enabled) return;
        long now = clock.getAsLong();
        if (now - startedAt < graceMillis) return;
        if (now - lastPollMillis > thresholdMillis) {
            fired = true;
            try {
                onIdle.run();
            } finally {
                scheduler.shutdown();
            }
        }
    }
}
