package com.fvd.quarkiverse;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class QuarkiverseTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("app.quarkiverse.enabled", "true");
    }
}
