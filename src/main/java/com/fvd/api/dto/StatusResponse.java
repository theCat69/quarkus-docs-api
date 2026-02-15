package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusResponse {

    public boolean ready;
    public List<String> cachedVersions;
    public WarmupProgress warmupProgress;

    @RegisterForReflection
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WarmupProgress {
        public int completed;
        public int total;
        public List<String> versionsCompleted;
        public String currentVersion;
    }
}
