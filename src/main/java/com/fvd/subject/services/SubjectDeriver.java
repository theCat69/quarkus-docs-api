package com.fvd.subject.services;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.subject.Subject;
import com.fvd.subject.SubjectConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for deriving subjects from file paths based on configurable patterns.
 * 
 * <p>Subject derivation follows this precedence:
 * <ol>
 *   <li>Exact path overrides (configuration)</li>
 *   <li>Glob pattern overrides (configuration)</li>
 *   <li>Regex pattern matching (evaluated in order, first match wins)</li>
 *   <li>Default to "misc" subject</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SubjectDeriver {

    public static final String DEFAULT_SUBJECT = "misc";

    private static final Map<String, String> CATEGORY_TO_SUBJECT = Map.ofEntries(
            Map.entry("getting-started", "getting-started"),
            Map.entry("core", "core-concepts"),
            Map.entry("web", "rest-apis"),
            Map.entry("data", "data-persistence"),
            Map.entry("security", "security"),
            Map.entry("messaging", "messaging"),
            Map.entry("cloud", "cloud"),
            Map.entry("observability", "observability"),
            Map.entry("tooling", "tooling"),
            Map.entry("compatibility", "core-concepts"),
            Map.entry("writing-extensions", "extensions"),
            Map.entry("miscellaneous", "misc"),
            Map.entry("integration", "messaging"),
            Map.entry("serialization", "rest-apis"),
            Map.entry("alternative-languages", "core-concepts"),
            Map.entry("business-automation", "extensions")
    );

    private static final Map<String, String> TOPIC_KEYWORDS_TO_SUBJECT = Map.ofEntries(
            Map.entry("rest", "rest-apis"),
            Map.entry("resteasy", "rest-apis"),
            Map.entry("http", "rest-apis"),
            Map.entry("servlet", "rest-apis"),
            Map.entry("websocket", "rest-apis"),
            Map.entry("graphql", "rest-apis"),
            Map.entry("security", "security"),
            Map.entry("oidc", "security"),
            Map.entry("jwt", "security"),
            Map.entry("oauth", "security"),
            Map.entry("keycloak", "security"),
            Map.entry("auth", "security"),
            Map.entry("hibernate", "data-persistence"),
            Map.entry("panache", "data-persistence"),
            Map.entry("jpa", "data-persistence"),
            Map.entry("jdbc", "data-persistence"),
            Map.entry("datasource", "data-persistence"),
            Map.entry("database", "data-persistence"),
            Map.entry("sql", "data-persistence"),
            Map.entry("nosql", "data-persistence"),
            Map.entry("mongodb", "data-persistence"),
            Map.entry("redis", "data-persistence"),
            Map.entry("kafka", "messaging"),
            Map.entry("amqp", "messaging"),
            Map.entry("messaging", "messaging"),
            Map.entry("reactive-messaging", "messaging"),
            Map.entry("rabbitmq", "messaging"),
            Map.entry("pulsar", "messaging"),
            Map.entry("kubernetes", "cloud"),
            Map.entry("openshift", "cloud"),
            Map.entry("docker", "cloud"),
            Map.entry("container", "cloud"),
            Map.entry("aws", "cloud"),
            Map.entry("azure", "cloud"),
            Map.entry("gcp", "cloud"),
            Map.entry("metrics", "observability"),
            Map.entry("health", "observability"),
            Map.entry("tracing", "observability"),
            Map.entry("opentelemetry", "observability"),
            Map.entry("micrometer", "observability"),
            Map.entry("logging", "observability"),
            Map.entry("test", "testing"),
            Map.entry("junit", "testing"),
            Map.entry("mock", "testing"),
            Map.entry("testing", "testing"),
            Map.entry("cdi", "core-concepts"),
            Map.entry("config", "core-concepts"),
            Map.entry("lifecycle", "core-concepts"),
            Map.entry("injection", "core-concepts"),
            Map.entry("bean", "core-concepts"),
            Map.entry("native", "core-concepts"),
            Map.entry("graalvm", "core-concepts"),
            Map.entry("getting-started", "getting-started"),
            Map.entry("quickstart", "getting-started"),
            Map.entry("tutorial", "getting-started"),
            Map.entry("extension", "extensions"),
            Map.entry("quarkiverse", "extensions"),
            Map.entry("cli", "tooling"),
            Map.entry("dev-services", "tooling"),
            Map.entry("devmode", "tooling"),
            Map.entry("ide", "tooling"),
            Map.entry("maven", "tooling"),
            Map.entry("gradle", "tooling"),
            Map.entry("quarkus-cli", "tooling")
    );

    private final SubjectConfig config;

    private final Map<String, Pattern> compiledPatterns = new LinkedHashMap<>();
    private final Map<String, String> patternToSubject = new LinkedHashMap<>();
    private final Map<String, PathMatcher> globMatchers = new HashMap<>();
    private final Map<String, Integer> subjectDocCounts = new ConcurrentHashMap<>();
    private Map<String, SubjectMetadata> cachedMetadataMap;

    @PostConstruct
    void init() {
        compilePatterns();
        compileGlobPatterns();
        cachedMetadataMap = buildMetadataMap();
        log.info("SubjectDeriver initialized with {} regex patterns and {} glob patterns",
                compiledPatterns.size(), globMatchers.size());
    }

    private void compilePatterns() {
        if (config.patterns() == null || config.patterns().isEmpty()) {
            log.debug("No subject patterns configured, using defaults");
            loadDefaultPatterns();
            return;
        }

        int flags = config.caseInsensitive() ? Pattern.CASE_INSENSITIVE : 0;
        for (SubjectConfig.SubjectPattern sp : config.patterns()) {
            try {
                Pattern pattern = Pattern.compile(sp.pattern(), flags);
                compiledPatterns.put(sp.pattern(), pattern);
                patternToSubject.put(sp.pattern(), sp.subject());
                log.debug("Compiled pattern '{}' -> subject '{}'", sp.pattern(), sp.subject());
            } catch (Exception e) {
                log.warn("Failed to compile pattern '{}': {}", sp.pattern(), e.getMessage());
            }
        }
    }

    private void loadDefaultPatterns() {
        int flags = config.caseInsensitive() ? Pattern.CASE_INSENSITIVE : 0;
        
        // Default patterns in priority order
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("(^|.*/)(getting-started|quickstart|tutorial).*", "getting-started");
        defaults.put("(^|.*/)cdi[-.].*|(^|.*/)lifecycle.*|(^|.*/)config(uration)?[-.].*", "core-concepts");
        defaults.put("(^|.*/)rest[-.].*|(^|.*/)resteasy.*|(^|.*/)json[-.].*|(^|.*/)jaxrs.*", "rest-apis");
        defaults.put("(^|.*/)hibernate.*|(^|.*/)panache.*|(^|.*/)datasource.*|(^|.*/)database.*|(^|.*/)jpa[-.].*|(^|.*/)jdbc[-.].*", "data-persistence");
        defaults.put("(^|.*/)security.*|(^|.*/)auth[-.].*|(^|.*/)oidc.*|(^|.*/)jwt[-.].*|(^|.*/)oauth.*|(^|.*/)keycloak.*", "security");
        defaults.put("(^|.*/)kafka.*|(^|.*/)amqp.*|(^|.*/)messaging.*|(^|.*/)reactive-messaging.*", "messaging");
        defaults.put("(^|.*/)kubernetes.*|(^|.*/)openshift.*|(^|.*/)docker.*|(^|.*/)container[-.].*|(^|.*/)cloud[-.].*", "cloud");
        defaults.put("(^|.*/)metrics.*|(^|.*/)health[-.].*|(^|.*/)tracing.*|(^|.*/)logging.*|(^|.*/)opentelemetry.*|(^|.*/)micrometer.*", "observability");
        defaults.put("(^|.*/)test(ing)?[-.].*|(^|.*/)mock[-.].*|(^|.*/)junit.*", "testing");
        defaults.put("(^|.*/)cli[-.].*|(^|.*/)dev-services.*|(^|.*/)ide[-.].*|(^|.*/)maven.*|(^|.*/)gradle.*", "tooling");
        defaults.put("(^|.*/)extension.*|(^|.*/)quarkiverse.*", "extensions");

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            try {
                Pattern pattern = Pattern.compile(entry.getKey(), flags);
                compiledPatterns.put(entry.getKey(), pattern);
                patternToSubject.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("Failed to compile default pattern '{}': {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void compileGlobPatterns() {
        if (config.globOverrides() == null) {
            return;
        }

        for (Map.Entry<String, String> entry : config.globOverrides().entrySet()) {
            try {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + entry.getKey());
                globMatchers.put(entry.getKey(), matcher);
                log.debug("Compiled glob pattern '{}' -> subject '{}'", entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("Failed to compile glob pattern '{}': {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Derive subject from file path.
     * 
     * <p>Uses the following precedence:
     * <ol>
     *   <li>Exact path overrides</li>
     *   <li>Glob pattern overrides</li>
     *   <li>Regex patterns (first match wins)</li>
     *   <li>Default to "misc"</li>
     * </ol>
     *
     * @param filePath the file path to categorize (relative path)
     * @return the derived subject name
     */
    public String deriveSubject(String filePath) {
        return deriveSubject(filePath, (DocumentMetadata) null);
    }

    /**
     * Derive subjects for multiple file paths and track counts.
     * 
     * @param filePaths the list of file paths
     * @return a map from file path to subject name
     */
    public Map<String, String> deriveSubjects(List<String> filePaths) {
        Map<String, String> result = new HashMap<>();
        for (String filePath : filePaths) {
            String subject = deriveSubject(filePath);
            result.put(filePath, subject);
        }
        return result;
    }

    /**
     * Derive subject using document metadata (primary) with path-regex fallback.
     *
     * <p>Uses the following precedence:
     * <ol>
     *   <li>Exact path overrides (configuration)</li>
     *   <li>Glob pattern overrides (configuration)</li>
     *   <li>{@code :categories:} metadata mapping</li>
     *   <li>{@code :topics:} metadata analysis (majority vote)</li>
     *   <li>Regex patterns (first match wins)</li>
     *   <li>Default to "misc"</li>
     * </ol>
     *
     * @param filePath the file path to categorize (relative path)
     * @param metadata the document metadata (may be null or empty)
     * @return the derived subject name
     */
    public String deriveSubject(String filePath, DocumentMetadata metadata) {
        if (!config.enabled()) {
            return DEFAULT_SUBJECT;
        }

        if (filePath == null || filePath.isBlank()) {
            return DEFAULT_SUBJECT;
        }

        String normalizedPath = normalizePath(filePath);

        // 1. Check exact overrides
        if (config.overrides() != null) {
            String override = config.overrides().get(normalizedPath);
            if (override != null) {
                log.trace("Path '{}' matched exact override -> '{}'", filePath, override);
                return override;
            }
        }

        // 2. Check glob pattern overrides
        if (config.globOverrides() != null) {
            for (Map.Entry<String, String> entry : config.globOverrides().entrySet()) {
                PathMatcher matcher = globMatchers.get(entry.getKey());
                if (matcher != null && matcher.matches(Paths.get(normalizedPath))) {
                    log.trace("Path '{}' matched glob '{}' -> '{}'", filePath, entry.getKey(), entry.getValue());
                    return entry.getValue();
                }
            }
        }

        // 3. Check :categories: metadata
        if (metadata != null && metadata.hasCategories()) {
            String subject = mapCategoryToSubject(metadata.getCategories());
            if (subject != null) {
                log.trace("Path '{}' classified by categories {} -> '{}'",
                        filePath, metadata.getCategories(), subject);
                return subject;
            }
        }

        // 4. Check :topics: metadata
        if (metadata != null && metadata.hasTopics()) {
            String subject = mapTopicsToSubject(metadata.getTopics());
            if (subject != null) {
                log.trace("Path '{}' classified by topics {} -> '{}'",
                        filePath, metadata.getTopics(), subject);
                return subject;
            }
        }

        // 5. Check regex patterns (in order)
        for (Map.Entry<String, Pattern> entry : compiledPatterns.entrySet()) {
            if (entry.getValue().matcher(normalizedPath).matches()) {
                String subject = patternToSubject.get(entry.getKey());
                log.trace("Path '{}' matched pattern '{}' -> '{}'", filePath, entry.getKey(), subject);
                return subject;
            }
        }

        // 6. Default
        log.trace("Path '{}' did not match any pattern -> '{}'", filePath, DEFAULT_SUBJECT);
        return DEFAULT_SUBJECT;
    }

    /**
     * Derive subjects for multiple file paths using metadata when available.
     *
     * @param filePaths the list of file paths
     * @param metadataByPath map of file path to DocumentMetadata
     * @return a map from file path to subject name
     */
    public Map<String, String> deriveSubjects(List<String> filePaths,
                                               Map<String, DocumentMetadata> metadataByPath) {
        if (filePaths == null || filePaths.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        for (String filePath : filePaths) {
            DocumentMetadata metadata = metadataByPath.get(filePath);
            String subject = deriveSubject(filePath, metadata);
            result.put(filePath, subject);
        }
        return result;
    }

    /**
     * Record that a file has been assigned to a subject.
     * Used for tracking document counts.
     *
     * @param subject the subject name
     */
    public void recordDocument(String subject) {
        subjectDocCounts.merge(subject, 1, Integer::sum);
    }

    /**
     * Reset document counts (typically before re-indexing).
     */
    public void resetDocCounts() {
        subjectDocCounts.clear();
    }

    /**
     * Get all defined subjects with their metadata.
     *
     * @return list of all subjects
     */
    public List<Subject> getAllSubjects() {
        List<Subject> subjects = new ArrayList<>();
        
        for (Map.Entry<String, SubjectMetadata> entry : cachedMetadataMap.entrySet()) {
            String name = entry.getKey();
            SubjectMetadata meta = entry.getValue();
            int docCount = subjectDocCounts.getOrDefault(name, 0);
            
            subjects.add(new Subject(
                    name,
                    meta.displayName,
                    meta.description,
                    docCount,
                    meta.keywords
            ));
        }
        
        return subjects;
    }

    /**
     * Get subjects that have at least one document.
     *
     * @return list of subjects with docCount > 0
     */
    public List<Subject> getSubjectsWithDocs() {
        return getAllSubjects().stream()
                .filter(s -> s.docCount() > 0)
                .toList();
    }

    /**
     * Get subject by name.
     *
     * @param name the subject name
     * @return the subject if found
     */
    public Optional<Subject> getSubject(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        SubjectMetadata meta = cachedMetadataMap.get(name);
        
        if (meta == null) {
            return Optional.empty();
        }

        int docCount = subjectDocCounts.getOrDefault(name, 0);
        return Optional.of(new Subject(
                name,
                meta.displayName,
                meta.description,
                docCount,
                meta.keywords
        ));
    }

    /**
     * Returns the set of valid subject names from the cached metadata map.
     *
     * @return immutable set of all defined subject names
     */
    public Set<String> getValidSubjectNames() {
        return Set.copyOf(cachedMetadataMap.keySet());
    }

    private Map<String, SubjectMetadata> buildMetadataMap() {
        Map<String, SubjectMetadata> result = new LinkedHashMap<>();
        
        // Add defaults first
        result.putAll(getDefaultMetadata());
        
        // Override with configured definitions
        if (config.definitions() != null) {
            for (Map.Entry<String, SubjectConfig.SubjectMetadata> entry : config.definitions().entrySet()) {
                SubjectConfig.SubjectMetadata configMeta = entry.getValue();
                result.put(entry.getKey(), new SubjectMetadata(
                        configMeta.displayName(),
                        configMeta.description().orElse(""),
                        configMeta.keywords().orElse(List.of())
                ));
            }
        }
        
        return result;
    }

    private Map<String, SubjectMetadata> getDefaultMetadata() {
        Map<String, SubjectMetadata> defaults = new LinkedHashMap<>();
        
        defaults.put("getting-started", new SubjectMetadata(
                "Getting Started",
                "Quickstarts, tutorials, first steps",
                List.of("quickstart", "tutorial", "getting-started", "introduction")
        ));
        
        defaults.put("core-concepts", new SubjectMetadata(
                "Core Concepts",
                "CDI, configuration, lifecycle",
                List.of("cdi", "configuration", "lifecycle", "injection", "beans")
        ));
        
        defaults.put("rest-apis", new SubjectMetadata(
                "REST APIs",
                "RESTEasy, REST clients, JSON",
                List.of("rest", "resteasy", "json", "jaxrs", "http", "api")
        ));
        
        defaults.put("data-persistence", new SubjectMetadata(
                "Data & Persistence",
                "Hibernate, Panache, databases",
                List.of("hibernate", "panache", "database", "jpa", "jdbc", "sql")
        ));
        
        defaults.put("security", new SubjectMetadata(
                "Security",
                "Authentication, authorization, crypto",
                List.of("security", "authentication", "authorization", "oidc", "jwt", "oauth")
        ));
        
        defaults.put("messaging", new SubjectMetadata(
                "Messaging",
                "Kafka, AMQP, reactive messaging",
                List.of("kafka", "amqp", "messaging", "reactive", "events")
        ));
        
        defaults.put("cloud", new SubjectMetadata(
                "Cloud & Containers",
                "Kubernetes, Docker, OpenShift",
                List.of("kubernetes", "docker", "openshift", "container", "cloud", "k8s")
        ));
        
        defaults.put("observability", new SubjectMetadata(
                "Observability",
                "Metrics, health, tracing, logging",
                List.of("metrics", "health", "tracing", "logging", "monitoring", "opentelemetry")
        ));
        
        defaults.put("testing", new SubjectMetadata(
                "Testing",
                "JUnit, test frameworks, mocking",
                List.of("test", "junit", "mock", "testing", "integration")
        ));
        
        defaults.put("tooling", new SubjectMetadata(
                "Tooling",
                "CLI, Dev Services, IDE support",
                List.of("cli", "dev-services", "ide", "maven", "gradle", "tools")
        ));
        
        defaults.put("extensions", new SubjectMetadata(
                "Extensions",
                "Extension development, Quarkiverse",
                List.of("extension", "quarkiverse", "plugin", "add-on")
        ));
        
        defaults.put("misc", new SubjectMetadata(
                "Miscellaneous",
                "Default for unmatched documents",
                List.of()
        ));
        
        return defaults;
    }

    private String mapCategoryToSubject(List<String> categories) {
        for (String category : categories) {
            String subject = CATEGORY_TO_SUBJECT.get(category.toLowerCase().trim());
            if (subject != null) {
                return subject;
            }
        }
        return null;
    }

    private String mapTopicsToSubject(List<String> topics) {
        // Count votes per subject using LinkedHashMap to preserve insertion order
        // for deterministic tie-breaking
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (String topic : topics) {
            String subject = TOPIC_KEYWORDS_TO_SUBJECT.get(topic.toLowerCase().trim());
            if (subject != null) {
                votes.merge(subject, 1, Integer::sum);
            }
        }
        if (votes.isEmpty()) {
            return null;
        }
        // Return subject with most votes; on tie, the subject that was first encountered
        // in topic iteration order wins (deterministic via LinkedHashMap insertion order)
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String normalizePath(String filePath) {
        // Normalize to forward slashes and lowercase for matching
        String normalized = filePath.replace('\\', '/');
        if (config.caseInsensitive()) {
            normalized = normalized.toLowerCase();
        }
        return normalized;
    }

    /**
     * Internal metadata holder.
     */
    private record SubjectMetadata(String displayName, String description, List<String> keywords) {
    }
}
