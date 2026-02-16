package com.fvd.cache.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class WarmupStatusTracker {

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean warmupStarted = new AtomicBoolean(false);
    private final AtomicReference<String> currentVersion = new AtomicReference<>(null);
    private final List<String> completedVersions = Collections.synchronizedList(new ArrayList<>());
    @Getter
    private volatile int totalVersions = 0;

    public void warmupStarted(List<String> versions) {
        warmupStarted.set(true);
        totalVersions = versions.size();
    }

    public void versionStarted(String version) {
        currentVersion.set(version);
    }

    public void versionCompleted(String version) {
        completedVersions.add(version);
        currentVersion.set(null);
    }

    public void warmupCompleted() {
        ready.set(true);
        currentVersion.set(null);
    }

    public boolean isReady() {
        return ready.get();
    }

    public boolean isWarmupStarted() {
        return warmupStarted.get();
    }

    public String getCurrentVersion() {
        return currentVersion.get();
    }

    public List<String> getCompletedVersions() {
        return List.copyOf(completedVersions);
    }

    public int getCompletedCount() {
        return completedVersions.size();
    }

    /**
     * Resets all state to initial values. Intended for test use only.
     */
    public void reset() {
        ready.set(false);
        warmupStarted.set(false);
        currentVersion.set(null);
        completedVersions.clear();
        totalVersions = 0;
    }
}
