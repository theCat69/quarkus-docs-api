package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * JAX-RS response filter that adds Cache-Control and ETag headers to successful API GET responses.
 * Supports conditional GET via If-None-Match → 304 Not Modified.
 * Runs after {@link FieldSelectionFilter} so ETags are computed on the final (field-filtered) entity.
 */
@Slf4j
@Provider
@Priority(Priorities.ENTITY_CODER)
@RegisterForReflection
public class CacheHeaderFilter implements ContainerResponseFilter {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.cache.http.max-age.versioned", defaultValue = "3600")
    int maxAgeVersioned;

    @ConfigProperty(name = "app.cache.http.max-age.main", defaultValue = "900")
    int maxAgeMain;

    @ConfigProperty(name = "app.cache.http.max-age.catalog", defaultValue = "1800")
    int maxAgeCatalog;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // Only apply to GET requests — skip POST (batch endpoint), PUT, DELETE, etc.
        if (!"GET".equals(request.getMethod())) return;

        // Only apply to successful responses (2xx)
        if (response.getStatus() < 200 || response.getStatus() >= 300) return;

        // Only apply to API paths — skip health (/q/health), OpenAPI (/q/openapi),
        // Swagger UI (/q/swagger-ui), dev UI (/q/dev-ui), and any other non-API paths
        String rawPath = request.getUriInfo().getPath();
        // Normalize: strip leading slash if present for consistent matching
        String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (!path.startsWith("api/")) return;

        // Skip status endpoint — must never be cached (real-time warmup state)
        if (path.startsWith("api/status")) return;

        // Skip meta endpoint — MetaResource already sets its own Cache-Control header
        if (path.startsWith("api/meta")) return;

        // Determine max-age based on path and version parameter
        String version = request.getUriInfo().getQueryParameters().getFirst("version");
        int maxAge = resolveMaxAge(path, version);

        response.getHeaders().putSingle("Cache-Control", "public, max-age=" + maxAge);

        // Compute ETag from response entity
        Object entity = response.getEntity();
        if (entity != null) {
            String etag = computeETag(entity);
            response.getHeaders().putSingle("ETag", "\"" + etag + "\"");

            // Conditional GET: check If-None-Match
            String ifNoneMatch = request.getHeaderString("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals("\"" + etag + "\"")) {
                response.setStatus(304);
                response.setEntity(null);
                response.getHeaders().remove("Content-Type");
                return;
            }
        }
    }

    int resolveMaxAge(String path, String version) {
        if (path.startsWith("api/catalog")) return maxAgeCatalog;
        if ("main".equals(version) || version == null) return maxAgeMain;
        return maxAgeVersioned;
    }

    String computeETag(Object entity) {
        try {
            byte[] content;
            if (entity instanceof byte[] bytes) {
                // FieldSelectionFilter produces byte[] via objectMapper.writeValueAsBytes()
                content = bytes;
            } else {
                // No field selection — serialize entity to bytes for hashing
                content = objectMapper.writeValueAsBytes(entity);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            return HexFormat.of().formatHex(hash, 0, 8); // 16 hex chars
        } catch (Exception e) {
            return "fallback";
        }
    }
}
