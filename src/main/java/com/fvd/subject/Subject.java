package com.fvd.subject;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Represents a documentation subject category.
 *
 * @param name the subject ID in kebab-case (e.g., "getting-started")
 * @param displayName the human-readable display name
 * @param description a brief description of the subject
 * @param docCount the number of documents in this subject
 * @param keywords representative keywords for this subject
 */
@RegisterForReflection
public record Subject(
        String name,
        String displayName,
        String description,
        int docCount,
        List<String> keywords
) {
}
