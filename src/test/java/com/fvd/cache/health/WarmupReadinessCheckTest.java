package com.fvd.cache.health;

import com.fvd.cache.services.WarmupStatusTracker;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WarmupReadinessCheckTest {

    private WarmupStatusTracker tracker;
    private WarmupReadinessCheck healthCheck;

    @BeforeEach
    void setUp() {
        tracker = new WarmupStatusTracker();
        healthCheck = new WarmupReadinessCheck(tracker);
    }

    @Test
    void shouldReturnDownWhenNotReady() {
        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getName()).isEqualTo("Cache warmup");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get().get("completedVersions")).isEqualTo(0L);
        assertThat(response.getData().get().get("totalVersions")).isEqualTo(0L);
    }

    @Test
    void shouldReturnUpWhenReady() {
        tracker.warmupStarted(List.of("3.20", "3.27"));
        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");
        tracker.versionStarted("3.27");
        tracker.versionCompleted("3.27");
        tracker.warmupCompleted();

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getName()).isEqualTo("Cache warmup");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get().get("completedVersions")).isEqualTo(2L);
        assertThat(response.getData().get().get("totalVersions")).isEqualTo(2L);
    }

    @Test
    void shouldReturnDownDuringWarmup() {
        tracker.warmupStarted(List.of("3.20", "3.27", "main"));
        tracker.versionStarted("3.20");
        tracker.versionCompleted("3.20");

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getName()).isEqualTo("Cache warmup");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get().get("completedVersions")).isEqualTo(1L);
        assertThat(response.getData().get().get("totalVersions")).isEqualTo(3L);
    }
}
