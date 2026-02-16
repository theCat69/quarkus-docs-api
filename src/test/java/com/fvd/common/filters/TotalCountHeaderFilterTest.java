package com.fvd.common.filters;

import com.fvd.api.dto.PaginatedResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotalCountHeaderFilterTest {

    private TotalCountHeaderFilter filter;
    private ContainerRequestContext request;
    private ContainerResponseContext response;
    private MultivaluedMap<String, Object> responseHeaders;

    @BeforeEach
    void setUp() {
        filter = new TotalCountHeaderFilter();
        request = mock(ContainerRequestContext.class);
        response = mock(ContainerResponseContext.class);
        responseHeaders = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(responseHeaders);
    }

    @Test
    void shouldAddTotalCountHeaderForPaginatedResponse() {
        PaginatedResponse<String> paginated = PaginatedResponse.of(
                List.of("a", "b", "c"), 42, 0, 20);
        when(response.getEntity()).thenReturn(paginated);

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("X-Total-Count")).isEqualTo("42");
    }

    @Test
    void shouldNotAddHeaderForNonPaginatedEntity() {
        when(response.getEntity()).thenReturn("just a string");

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("X-Total-Count")).isFalse();
    }

    @Test
    void shouldNotAddHeaderForNullEntity() {
        when(response.getEntity()).thenReturn(null);

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("X-Total-Count")).isFalse();
    }
}
