package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fvd.common.config.FieldSelectionObjectMapperCustomizer;
import com.fvd.common.validators.FieldSelectionValidator;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * JAX-RS response filter that applies field selection based on the {@code fields} query parameter.
 * When present, only the requested fields are included in the JSON response.
 * Uses {@code byte[]} entity replacement to avoid double-serialization in RESTEasy Reactive.
 */
@Slf4j
@ApplicationScoped
@Provider
@Priority(Priorities.ENTITY_CODER + 100)
@RegisterForReflection
@RequiredArgsConstructor
public class FieldSelectionFilter implements ContainerResponseFilter {

    private final ObjectMapper objectMapper;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String fieldsParam = request.getUriInfo().getQueryParameters().getFirst("fields");
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return;
        }

        if (response.getStatus() >= 400) {
            return;
        }

        Object entity = response.getEntity();
        if (entity == null) {
            return;
        }

        Set<String> requestedFields = FieldSelectionValidator.parseAndValidate(fieldsParam, entity);
        if (requestedFields.isEmpty()) {
            return;
        }

        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter(FieldSelectionObjectMapperCustomizer.FILTER_NAME,
                        SimpleBeanPropertyFilter.filterOutAllExcept(requestedFields))
                .setFailOnUnknownId(false);

        try {
            byte[] json = objectMapper.writer(filterProvider).writeValueAsBytes(entity);
            response.setEntity(json);
            response.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
        } catch (Exception e) {
            log.warn("Failed to apply field selection filter, returning full response", e);
        }
    }
}
