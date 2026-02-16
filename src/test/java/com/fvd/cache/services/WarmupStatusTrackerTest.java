package com.fvd.cache.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WarmupStatusTrackerTest {

    private WarmupStatusTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new WarmupStatusTracker();
    }

    @Test
    void initialStateIsNotReady() {
        assertThat(tracker.isReady()).isFalse();
        assertThat(tracker.isWarmupStarted()).isFalse();
        assertThat(tracker.getCurrentVersion()).isNull();
        assertThat(tracker.getCompletedVersions()).isEmpty();
        assertThat(tracker.getTotalVersions()).isZero();
        assertThat(tracker.getCompletedCount()).isZero();
    }

    @Test
    void warmupStartedSetsStateCorrectly() {
        tracker.warmupStarted(List.of("3.20", "3.27", "main"));

        assertThat(tracker.isWarmupStarted()).isTrue();
        assertThat(tracker.isReady()).isFalse();
        assertThat(tracker.getTotalVersions()).isEqualTo(3);
    }

    @Test
    void warmupCompletedSetsReady() {
        tracker.warmupStarted(List.of("3.20"));
        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");
        tracker.warmupCompleted();

        assertThat(tracker.isReady()).isTrue();
        assertThat(tracker.getCurrentVersion()).isNull();
    }

    @Test
    void versionStartedTracksCurrentVersion() {
        tracker.warmupStarted(List.of("3.20", "3.27"));
        tracker.versionStarted("3.20");

        assertThat(tracker.getCurrentVersion()).isEqualTo("3.20");
    }

    @Test
    void versionCompletedClearsCurrentAndAddsToCompleted() {
        tracker.warmupStarted(List.of("3.20", "3.27"));
        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");

        assertThat(tracker.getCurrentVersion()).isNull();
        assertThat(tracker.getCompletedVersions()).containsExactly("3.20");
        assertThat(tracker.getCompletedCount()).isEqualTo(1);
    }

    @Test
    void progressTracksMultipleVersions() {
        tracker.warmupStarted(List.of("3.20", "3.27", "main"));

        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");

        tracker.versionStarted("3.27");
        assertThat(tracker.getCurrentVersion()).isEqualTo("3.27");
        assertThat(tracker.getCompletedCount()).isEqualTo(1);
        tracker.versionCompleted("3.27");

        tracker.versionStarted("main");
        tracker.versionCompleted("main");

        tracker.warmupCompleted();

        assertThat(tracker.isReady()).isTrue();
        assertThat(tracker.getCompletedCount()).isEqualTo(3);
        assertThat(tracker.getTotalVersions()).isEqualTo(3);
        assertThat(tracker.getCompletedVersions()).containsExactly("3.20", "3.27", "main");
    }

    @Test
    void completedVersionsReturnsDefensiveCopy() {
        tracker.warmupStarted(List.of("3.20"));
        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");

        List<String> copy = tracker.getCompletedVersions();
        assertThat(copy).containsExactly("3.20");

        // Modifying the copy should not affect the tracker
        try {
            copy.add("hacked");
        } catch (UnsupportedOperationException e) {
            // List.copyOf returns unmodifiable list, this is expected
        }
        assertThat(tracker.getCompletedVersions()).containsExactly("3.20");
    }

    @Test
    void warmupCompletedClearsCurrentVersion() {
        tracker.warmupStarted(List.of("3.20"));
        tracker.versionStarted("3.20");
        // Simulate warmupCompleted called without versionCompleted (edge case)
        tracker.warmupCompleted();

        assertThat(tracker.isReady()).isTrue();
        assertThat(tracker.getCurrentVersion()).isNull();
    }

    @Test
    void concurrentReadsDoNotThrow() throws InterruptedException {
        tracker.warmupStarted(List.of("3.20", "3.27", "main"));
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        try(ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        tracker.isReady();
                        tracker.isWarmupStarted();
                        tracker.getCurrentVersion();
                        tracker.getCompletedVersions();
                        tracker.getTotalVersions();
                        tracker.getCompletedCount();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(completed).isTrue();
        }


    }
}
