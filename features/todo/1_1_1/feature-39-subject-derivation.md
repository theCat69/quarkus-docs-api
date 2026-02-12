# Feature 39: Subject Derivation

## Summary

Version 1.1.1 introduces automatic subject/category derivation for documentation files. Subjects are primarily derived from document path patterns, with optional configuration overrides for edge cases.

## User Story

**As an** AI agent browsing Quarkus documentation  
**I want** documents automatically categorized into subjects  
**So that** I can filter and discover related documentation efficiently

## Motivation

The Quarkus documentation repository contains hundreds of documents across various topics. Without categorization, AI agents must scan all documents or rely solely on keyword search. Subject categorization enables hierarchical browsing and filtered searches.

---

## Requirements

### Subject Derivation Priority

| Priority | Source | Description |
|----------|--------|-------------|
| 1 | Configuration Override | Explicit mapping in config file |
| 2 | Path Pattern Matching | Regex patterns against file paths |
| 3 | Default Subject | Fallback for unmatched documents |

### Standard Subjects

| Subject ID | Display Name | Description |
|------------|--------------|-------------|
| `getting-started` | Getting Started | Quickstarts, tutorials, first steps |
| `core-concepts` | Core Concepts | CDI, configuration, lifecycle |
| `rest-apis` | REST APIs | RESTEasy, REST clients, JSON |
| `data-persistence` | Data & Persistence | Hibernate, Panache, databases |
| `security` | Security | Authentication, authorization, crypto |
| `messaging` | Messaging | Kafka, AMQP, reactive messaging |
| `cloud` | Cloud & Containers | Kubernetes, Docker, OpenShift |
| `observability` | Observability | Metrics, health, tracing, logging |
| `testing` | Testing | JUnit, test frameworks, mocking |
| `tooling` | Tooling | CLI, Dev Services, IDE support |
| `extensions` | Extensions | Extension development, Quarkiverse |
| `misc` | Miscellaneous | Default for unmatched documents |

---

## Detailed Requirements

### R1: Path Pattern Matching

**Description:** Define regex patterns to match document paths to subjects.

**Acceptance Criteria:**
- [ ] Patterns evaluated in order, first match wins
- [ ] Patterns support standard Java regex
- [ ] Patterns match against full relative path
- [ ] Case-insensitive matching by default

**Default Pattern Configuration:**

```yaml
subject-patterns:
  - pattern: ".*/(getting-started|quickstart|tutorial).*"
    subject: getting-started
    
  - pattern: ".*/cdi.*|.*/lifecycle.*|.*/config(uration)?.*"
    subject: core-concepts
    
  - pattern: ".*/rest.*|.*/resteasy.*|.*/json.*|.*/jaxrs.*"
    subject: rest-apis
    
  - pattern: ".*/hibernate.*|.*/panache.*|.*/datasource.*|.*/database.*|.*/jpa.*|.*/jdbc.*"
    subject: data-persistence
    
  - pattern: ".*/security.*|.*/auth.*|.*/oidc.*|.*/jwt.*|.*/oauth.*|.*/keycloak.*"
    subject: security
    
  - pattern: ".*/kafka.*|.*/amqp.*|.*/messaging.*|.*/reactive-messaging.*"
    subject: messaging
    
  - pattern: ".*/kubernetes.*|.*/openshift.*|.*/docker.*|.*/container.*|.*/cloud.*"
    subject: cloud
    
  - pattern: ".*/metrics.*|.*/health.*|.*/tracing.*|.*/logging.*|.*/opentelemetry.*|.*/micrometer.*"
    subject: observability
    
  - pattern: ".*/test.*|.*/mock.*|.*/junit.*"
    subject: testing
    
  - pattern: ".*/cli.*|.*/dev-services.*|.*/ide.*|.*/maven.*|.*/gradle.*"
    subject: tooling
    
  - pattern: ".*/extension.*|.*/quarkiverse.*"
    subject: extensions
```

### R2: Configuration Overrides

**Description:** Allow explicit path-to-subject mappings in configuration.

**Acceptance Criteria:**
- [ ] Overrides take precedence over pattern matching
- [ ] Support exact path matches
- [ ] Support glob patterns for groups of files
- [ ] Configuration in `application.properties` or YAML

**Configuration Format:**

```yaml
# application.yaml
quarkus-docs:
  subject-overrides:
    # Exact path override
    "_guides/all-builtin-config.adoc": "core-concepts"
    
    # Glob pattern override
    "_guides/platform-*.adoc": "core-concepts"
    
    # Multiple files to same subject
    "_posts/**.adoc": "misc"
```

**Properties Format:**

```properties
# application.properties
quarkus-docs.subject-overrides."_guides/all-builtin-config.adoc"=core-concepts
quarkus-docs.subject-overrides."_guides/platform-*.adoc"=core-concepts
```

### R3: Subject Metadata

**Description:** Each subject includes metadata for API responses.

**Acceptance Criteria:**
- [ ] `name`: Machine-readable identifier (kebab-case)
- [ ] `displayName`: Human-readable label
- [ ] `description`: Brief description of subject scope
- [ ] `docCount`: Number of documents in subject
- [ ] `keywords`: Representative keywords for the subject

**Subject Definition:**

```java
@RegisterForReflection
public record Subject(
    String name,
    String displayName,
    String description,
    int docCount,
    List<String> keywords
) {}
```

### R4: Dynamic Subject Discovery

**Description:** Subjects with no matching documents are omitted from catalog.

**Acceptance Criteria:**
- [ ] Only subjects with `docCount > 0` appear in catalog
- [ ] Subject list recalculated on re-index
- [ ] Cached per version

### R5: Subject Keywords

**Description:** Each subject has associated keywords for discovery.

**Acceptance Criteria:**
- [ ] Keywords derived from subject's documents
- [ ] Top N most frequent keywords selected
- [ ] Keywords help users discover relevant subjects

**Default Subject Keywords:**

| Subject | Keywords |
|---------|----------|
| `getting-started` | quickstart, tutorial, hello-world, first, beginner |
| `core-concepts` | cdi, injection, beans, configuration, lifecycle |
| `rest-apis` | rest, endpoint, resource, json, http, client |
| `data-persistence` | database, hibernate, panache, entity, repository, jpa |
| `security` | authentication, authorization, jwt, oauth, oidc, roles |
| `messaging` | kafka, amqp, message, event, reactive, stream |
| `cloud` | kubernetes, docker, container, deployment, pod, openshift |
| `observability` | metrics, health, tracing, logging, monitoring, spans |
| `testing` | test, junit, mock, integration, unit, assertion |
| `tooling` | cli, devservices, maven, gradle, ide, plugin |
| `extensions` | extension, quarkiverse, spi, processor, build |

---

## API Impact

### Catalog Response

```json
{
  "subjects": [
    {
      "name": "security",
      "displayName": "Security",
      "description": "Authentication, authorization, and security configurations",
      "docCount": 15,
      "keywords": ["authentication", "authorization", "jwt", "oauth", "oidc"]
    }
  ]
}
```

### Document Response

Each document includes its derived subject:

```json
{
  "title": "Security JWT Authentication",
  "path": "_guides/security-jwt.adoc",
  "subject": "security",
  ...
}
```

### Search/Filter Support

All search endpoints support `subject` filter:

```
GET /api/documents?keywords=authentication&subject=security
GET /api/code-samples?keywords=entity&subject=data-persistence
GET /api/search?keywords=config&subject=core-concepts
```

---

## Technical Implementation Notes

### Pattern Evaluation

```java
public String deriveSubject(String filePath) {
    // 1. Check overrides first
    String override = subjectOverrides.get(filePath);
    if (override != null) {
        return override;
    }
    
    // 2. Check glob overrides
    for (var entry : globOverrides.entrySet()) {
        if (globMatcher.matches(entry.getKey(), filePath)) {
            return entry.getValue();
        }
    }
    
    // 3. Pattern matching
    for (var pattern : subjectPatterns) {
        if (pattern.matcher().matches(filePath)) {
            return pattern.subject();
        }
    }
    
    // 4. Default
    return "misc";
}
```

### Caching Strategy

- Subject assignments computed during indexing
- Stored in keyword index alongside document metadata
- Catalog endpoint returns cached aggregation

### Configuration Loading

- Patterns loaded from `application.yaml` or `application.properties`
- Supports runtime reload via Quarkus config
- Default patterns compiled at startup

---

## Dependencies

- Quarkus Config (SmallRye Config)
- Java regex (java.util.regex.Pattern)
- Glob matching utility

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Ambiguous patterns | Document assigned wrong subject | Pattern order matters, test thoroughly |
| Too many subjects | Fragmented navigation | Limit to ~12 standard subjects |
| Missing patterns | Documents fall to "misc" | Review misc bucket periodically |
| Pattern performance | Slow indexing | Compile patterns once, cache |
