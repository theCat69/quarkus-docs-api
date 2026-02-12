package com.fvd.subject;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for subject derivation from file paths.
 * Uses prefix "app.subjects" for all settings.
 */
@ConfigMapping(prefix = "app.subjects")
public interface SubjectConfig {

    /**
     * Whether subject derivation is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Whether pattern matching should be case-insensitive.
     */
    @WithDefault("true")
    boolean caseInsensitive();

    /**
     * Exact path overrides - maps file paths to subject names.
     * Takes precedence over pattern matching.
     */
    Map<String, String> overrides();

    /**
     * Glob pattern overrides - maps glob patterns to subject names.
     * Evaluated after exact overrides but before regex patterns.
     */
    Map<String, String> globOverrides();

    /**
     * Subject pattern configurations.
     */
    List<SubjectPattern> patterns();

    /**
     * Subject metadata definitions.
     */
    Map<String, SubjectMetadata> definitions();

    /**
     * A regex pattern that maps to a subject.
     */
    interface SubjectPattern {
        /**
         * The regex pattern to match against file paths.
         */
        String pattern();

        /**
         * The subject name to assign when pattern matches.
         */
        String subject();
    }

    /**
     * Metadata for a subject.
     */
    interface SubjectMetadata {
        /**
         * Display name for the subject.
         */
        String displayName();

        /**
         * Description of the subject.
         */
        Optional<String> description();

        /**
         * Representative keywords for the subject.
         */
        Optional<List<String>> keywords();
    }
}
