package com.fvd.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Singleton;

/**
 * Registers a default {@code serializeAll()} filter for the {@code fieldSelector} filter name.
 * This ensures DTOs annotated with {@code @JsonFilter("fieldSelector")} serialize normally
 * when no {@code fields} query parameter is provided.
 */
@Singleton
@RegisterForReflection
public class FieldSelectionObjectMapperCustomizer implements ObjectMapperCustomizer {

    public static final String FILTER_NAME = "fieldSelector";

    @Override
    public void customize(ObjectMapper objectMapper) {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter(FILTER_NAME, SimpleBeanPropertyFilter.serializeAll())
                .setFailOnUnknownId(false);
        objectMapper.setFilterProvider(filterProvider);
    }
}
