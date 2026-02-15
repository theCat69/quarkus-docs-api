package com.fvd.subject.services;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.indexs.stores.DocumentMetadataStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Centralizes metadata-aware subject derivation for use by all services.
 * Encapsulates the loading of DocumentMetadata and delegation to SubjectDeriver,
 * ensuring consistent classification across the entire API surface.
 *
 * Services should use this resolver instead of calling SubjectDeriver.deriveSubject() directly.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MetadataAwareSubjectResolver {

    private final SubjectDeriver subjectDeriver;
    private final DocumentMetadataStore documentMetadataStore;

    /**
     * Derive subject for a single document path using metadata.
     * Loads metadata lazily from the store.
     *
     * @param version  the documentation version
     * @param filePath the file path to categorize
     * @return the derived subject name
     */
    public String resolveSubject(String version, String filePath) {
        DocumentMetadata metadata = documentMetadataStore
                .readByPath(version, filePath).orElse(null);
        return subjectDeriver.deriveSubject(filePath, metadata);
    }

    /**
     * Load all metadata for a version, for use in batch operations.
     * Callers should load this once and pass it to resolveSubject(filePath, metadataMap).
     *
     * @param version the documentation version
     * @return map of file path to metadata
     */
    public Map<String, DocumentMetadata> loadMetadataMap(String version) {
        return documentMetadataStore.readAll(version);
    }

    /**
     * Derive subject for a single document path using a pre-loaded metadata map.
     * Use this in loops to avoid repeated database queries.
     *
     * @param filePath    the file path to categorize
     * @param metadataMap pre-loaded map of file path to metadata
     * @return the derived subject name
     */
    public String resolveSubject(String filePath, Map<String, DocumentMetadata> metadataMap) {
        DocumentMetadata metadata = metadataMap.get(filePath);
        return subjectDeriver.deriveSubject(filePath, metadata);
    }
}
