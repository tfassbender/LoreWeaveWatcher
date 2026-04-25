package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class IdleShutdownTest {

    @Test
    void doesNotFireDuringGraceWindowEvenWithNoPolls() {
        AtomicLong now = new AtomicLong(0);
        AtomicBoolean fired = new AtomicBoolean();
        IdleShutdown idle = new IdleShutdown(100, 1000, now::get, () -> fired.set(true));

        for (int t = 0; t < 1000; t += 50) {
            now.set(t);
            idle.tickNow();
        }
        assertThat(fired).isFalse();
    }

    @Test
    void firesAfterIdleThresholdPastGrace() {
        AtomicLong now = new AtomicLong(0);
        AtomicBoolean fired = new AtomicBoolean();
        IdleShutdown idle = new IdleShutdown(100, 1000, now::get, () -> fired.set(true));

        // Past grace, no polls.
        now.set(1500);
        idle.tickNow();
        assertThat(fired).isTrue();
    }

    @Test
    void pollResetsTheTimer() {
        AtomicLong now = new AtomicLong(0);
        AtomicBoolean fired = new AtomicBoolean();
        IdleShutdown idle = new IdleShutdown(100, 1000, now::get, () -> fired.set(true));

        // Past grace but still polling.
        now.set(1500);
        idle.recordPoll();
        idle.tickNow();
        assertThat(fired).isFalse();

        now.set(1550); // 50ms since last poll < 100ms threshold
        idle.tickNow();
        assertThat(fired).isFalse();

        now.set(1700); // 150ms since last poll > 100ms threshold
        idle.tickNow();
        assertThat(fired).isTrue();
    }

    @Test
    void firesAtMostOnce() {
        AtomicLong now = new AtomicLong(0);
        AtomicLong fireCount = new AtomicLong();
        IdleShutdown idle = new IdleShutdown(100, 1000, now::get, fireCount::incrementAndGet);

        now.set(2000);
        idle.tickNow();
        idle.tickNow();
        idle.tickNow();

        assertThat(fireCount.get()).isEqualTo(1);
    }
}
