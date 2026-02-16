package com.fvd.common.filters;

import com.fvd.api.dto.PaginatedResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS response filter that adds an {@code X-Total-Count} header to responses
 * whose entity is a {@link PaginatedResponse}.
 * <p>
 * Runs before {@link FieldSelectionFilter} (which converts entity to {@code byte[]})
 * so the entity is still a typed DTO when inspected.
 */
@Provider
@Priority(Priorities.ENTITY_CODER - 100)
public class TotalCountHeaderFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object entity = response.getEntity();
        if (entity instanceof PaginatedResponse<?> paginated) {
            response.getHeaders().putSingle("X-Total-Count",
                    String.valueOf(paginated.getTotalCount()));
        }
    }
}
