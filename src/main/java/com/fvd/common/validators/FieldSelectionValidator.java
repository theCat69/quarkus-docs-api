package com.fvd.common.validators;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.experimental.UtilityClass;

import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.CatalogResponse;
import com.fvd.api.dto.ChunkResult;
import com.fvd.api.dto.ChunkSearchResponse;
import com.fvd.api.dto.CodeSampleResult;
import com.fvd.api.dto.CodeSampleSearchResponse;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.dto.QuickSearchResponse;
import com.fvd.api.dto.RelatedDocumentRef;
import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.api.dto.SearchResultRef;
import com.fvd.common.exceptions.InvalidInputException;

/**
 * Parses and validates the {@code fields} query parameter against response DTO field names.
 * Throws {@link InvalidInputException} with available fields when invalid field names are requested.
 */
@UtilityClass
@RegisterForReflection
public class FieldSelectionValidator {

    private static final Map<Class<?>, Class<?>> ITEM_TYPE_REGISTRY = Map.of(
            QuickSearchResponse.class, SearchResultRef.class,
            DocumentSearchResponse.class, DocumentResponse.class,
            CodeSampleSearchResponse.class, CodeSampleResult.class,
            ChunkSearchResponse.class, ChunkResult.class,
            RelatedDocumentResponse.class, RelatedDocumentRef.class,
            BatchDocumentResponse.class, BatchDocumentResponse.class,
            CatalogResponse.class, CatalogResponse.class,
            DocumentResponse.class, DocumentResponse.class
    );

    /**
     * Parses a comma-separated fields string and validates each field name against the entity's DTO.
     *
     * @param fieldsParam comma-separated field names
     * @param entity      the response entity (used to resolve the item class)
     * @return set of validated field names, or empty set if input is effectively empty
     * @throws InvalidInputException if any field names are invalid
     */
    public static Set<String> parseAndValidate(String fieldsParam, Object entity) {
        Set<String> requested = Arrays.stream(fieldsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (requested.isEmpty()) {
            return Set.of();
        }

        Class<?> itemClass = resolveItemClass(entity);
        Set<String> available = getFieldNames(itemClass);

        Set<String> invalid = requested.stream()
                .filter(f -> !available.contains(f))
                .collect(Collectors.toSet());

        if (!invalid.isEmpty()) {
            throw new InvalidInputException(
                    "Unknown field(s): " + invalid.stream().sorted().collect(Collectors.joining(", ")) +
                            ". Available fields: " + available.stream().sorted().collect(Collectors.joining(", ")));
        }

        return requested;
    }

    /**
     * Returns the set of public field names for the given class.
     */
    static Set<String> getFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Resolves the item-level DTO class from the response entity.
     * For paginated responses, returns the item type (e.g., SearchResultRef).
     * For direct DTOs, returns the entity class itself.
     */
    static Class<?> resolveItemClass(Object entity) {
        Class<?> entityClass = entity.getClass();
        Class<?> itemClass = ITEM_TYPE_REGISTRY.get(entityClass);
        if (itemClass != null) {
            return itemClass;
        }
        return entityClass;
    }
}
