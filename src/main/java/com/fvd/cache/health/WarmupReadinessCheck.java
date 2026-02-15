package com.fvd.cache.health;

import com.fvd.cache.services.WarmupStatusTracker;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class WarmupReadinessCheck implements HealthCheck {

    private final WarmupStatusTracker warmupStatusTracker;

    @Override
    public HealthCheckResponse call() {
        boolean ready = warmupStatusTracker.isReady();
        return HealthCheckResponse.named("Cache warmup")
                .status(ready)
                .withData("completedVersions", warmupStatusTracker.getCompletedCount())
                .withData("totalVersions", warmupStatusTracker.getTotalVersions())
                .build();
    }
}
