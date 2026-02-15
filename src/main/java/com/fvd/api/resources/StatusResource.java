package com.fvd.api.resources;

import com.fvd.api.dto.StatusResponse;
import com.fvd.cache.services.CacheService;
import com.fvd.cache.services.WarmupStatusTracker;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/status")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Status", description = "API readiness and warmup status")
public class StatusResource {

    private final WarmupStatusTracker warmupStatusTracker;
    private final CacheService cacheService;

    @GET
    @Operation(
            summary = "Get API readiness and warmup status",
            description = "Returns the current warmup progress and readiness state. " +
                    "MCP servers should poll this endpoint until 'ready' is true before " +
                    "routing agent requests. Returns 503 when not ready."
    )
    @APIResponse(
            responseCode = "200",
            description = "API is ready",
            content = @Content(schema = @Schema(implementation = StatusResponse.class))
    )
    @APIResponse(
            responseCode = "503",
            description = "API is not ready — warmup in progress",
            content = @Content(schema = @Schema(implementation = StatusResponse.class))
    )
    public Response getStatus() {
        StatusResponse status = buildStatus();
        int httpStatus = status.ready ? 200 : 503;
        return Response.status(httpStatus).entity(status).build();
    }

    private StatusResponse buildStatus() {
        return StatusResponse.builder()
                .ready(warmupStatusTracker.isReady())
                .cachedVersions(cacheService.listCachedVersions())
                .warmupProgress(StatusResponse.WarmupProgress.builder()
                        .completed(warmupStatusTracker.getCompletedCount())
                        .total(warmupStatusTracker.getTotalVersions())
                        .versionsCompleted(warmupStatusTracker.getCompletedVersions())
                        .currentVersion(warmupStatusTracker.getCurrentVersion())
                        .build())
                .build();
    }
}
