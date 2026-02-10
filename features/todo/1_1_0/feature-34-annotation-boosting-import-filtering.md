# Feature 34: Code Sample Indexing — Annotation Boosting & Import Filtering

> **Dependencies**: None (independent). Can be implemented in any order.

Improve code sample search relevance by (1) boosting Java annotations from known framework packages with a score of 10, and (2) filtering import boosting to only boost imports from known packages instead of all imports.

## Scope and behavior

### Annotation boosting
- Detect annotations in code blocks: regex pattern `@([A-Z][a-zA-Z0-9_]+)` (e.g., `@ApplicationScoped`, `@Path`, `@Inject`).
- Only boost annotations that can be resolved to imports from known packages.
- Known packages are configurable via `search.boost.annotation-packages` with defaults: `io.quarkus`, `jakarta`, `org.eclipse.microprofile`, `javax`.
- Resolution: scan the code block's import statements. If an annotation name (simple class name) appears in an import from a known package, boost the annotation's stemmed token by `search.boost.annotation-boost` (default `10`).
- Example: code block has `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` in the body → the simple name "ApplicationScoped" matches the import's simple name, and `jakarta` is in the known packages list → boost `"applicationscop"` (stemmed) by 10.
- Annotations without matching imports from known packages are NOT boosted (they could be project-specific or from non-framework packages).
- If an annotation appears in both an import and the body, the annotation boost (10) and import boost (5) are additive — the annotation gets a total of 15.

### Import filtering
- Currently `applyImportBoost()` boosts ALL import statement tokens by `search.boost.import-boost` (default 5). This inflates scores for irrelevant imports (e.g., `java.util.List`, `java.io.IOException`).
- Change: only boost imports whose fully-qualified class name starts with a prefix in the `search.boost.annotation-packages` list.
- Import filtering check: for each import FQCN, check if it starts with any configured package prefix. Only boost if it does.
- Example: `import jakarta.ws.rs.GET;` → `jakarta` matches the known packages → boost. `import java.util.List;` → `java` is NOT in the known packages → skip.

### Configuration
- `search.boost.annotation-boost` — default `10`. Score boost for resolved annotations.
- `search.boost.annotation-packages` — default `io.quarkus,jakarta,org.eclipse.microprofile,javax`. Comma-separated list of package prefixes for annotation resolution and import filtering.

## Internal interfaces

- **`SearchConfig.Boost`** — add:
  ```java
  @WithDefault("10")
  int annotationBoost();

  @WithDefault("io.quarkus,jakarta,org.eclipse.microprofile,javax")
  String annotationPackages();
  ```

- **`CodeSampleIndexer`** — modify:
  - `applyImportBoost(String codeContent, Map<String, Integer> keywords)` — add known-package filtering. Only boost imports whose FQCN starts with a prefix in `searchConfig.boost().annotationPackages()` (accessed via the injected field).
  - Add `applyAnnotationBoost(String codeContent, Map<String, Integer> keywords)` — new method:
    1. Parse import statements to build a map of simple name → FQCN.
    2. Detect `@AnnotationName` patterns in code body.
    3. For each annotation, check if its simple name maps to an import from a known package.
    4. If yes, add `Stemmer.stem(annotationName.toLowerCase())` with score `searchConfig.boost().annotationBoost()`.
  - Call `applyAnnotationBoost()` from `buildEntriesForFile()` after `applyImportBoost()`.

- **`TestSearchConfig.TestBoost`** — add `annotationBoost()` returning `10` and `annotationPackages()` returning `"io.quarkus,jakarta,org.eclipse.microprofile,javax"`.

## Response shape

No structural changes. Code sample scores will change (some increase for annotation-heavy code, some decrease for non-framework imports) due to more targeted boosting.

## Tasks

- [ ] Add `annotationBoost()` and `annotationPackages()` to `SearchConfig.Boost` with `@WithDefault` annotations.
- [ ] Add `search.boost.annotation-boost=10` and `search.boost.annotation-packages=io.quarkus,jakarta,org.eclipse.microprofile,javax` to `application.properties`.
- [ ] Update `TestSearchConfig.TestBoost` with the new methods.
- [ ] Add unit tests for `CodeSampleIndexer.applyAnnotationBoost()`:
  - Annotation `@ApplicationScoped` with import `jakarta.enterprise.context.ApplicationScoped` → boosted at score 10.
  - Annotation `@Path` with import `jakarta.ws.rs.Path` → boosted at score 10.
  - Annotation `@Override` without any import → not boosted.
  - Annotation `@MyCustomAnnotation` with import `com.example.MyCustomAnnotation` → not boosted (not a known package).
  - Multiple annotations in same code block → each independently resolved and boosted.
  - Annotation appears but import is from non-known package → not boosted.
- [ ] Implement `CodeSampleIndexer.applyAnnotationBoost()`.
- [ ] Add unit tests for filtered `applyImportBoost()`:
  - Import `jakarta.ws.rs.GET` → boosted (known package `jakarta`).
  - Import `io.quarkus.runtime.Startup` → boosted (known package `io.quarkus`).
  - Import `java.util.List` → NOT boosted (not a known package).
  - Import `java.io.IOException` → NOT boosted.
  - Import `org.eclipse.microprofile.config.inject.ConfigProperty` → boosted (known package).
  - Static import `jakarta.ws.rs.core.MediaType.APPLICATION_JSON` → boosted.
- [ ] Update `CodeSampleIndexer.applyImportBoost()` — add known-package filtering using the injected `searchConfig.boost().annotationPackages()` field.
- [ ] Update `CodeSampleIndexer.buildEntriesForFile()` — call `applyAnnotationBoost()` after `applyImportBoost()`.
- [ ] Update existing `CodeSampleIndexerTest` tests for changed import boost behavior (some imports will no longer be boosted).
- [ ] Run all tests (`./gradlew test`) — all must pass.

## Acceptance Criteria

1. Annotations from known packages are boosted by `annotation-boost` (default 10).
2. Annotations without resolvable imports from known packages are NOT boosted.
3. Import boosting only applies to imports from known packages (`io.quarkus`, `jakarta`, `org.eclipse.microprofile`, `javax`).
4. Imports from `java.*` and other non-framework packages are no longer boosted.
5. Configuration is externalized via `search.boost.annotation-boost` and `search.boost.annotation-packages`.
6. All existing tests pass (some may need score adjustments due to import filtering changes).

## Operational notes

- **Score changes**: Existing code sample scores will change because imports from `java.*` are no longer boosted. This improves relevance but may affect result ordering.
- The annotation regex `@([A-Z][a-zA-Z0-9_]+)` is simple and may match annotation-like patterns in comments. This is acceptable — false positives are harmless (they just don't get boosted if they don't resolve to imports from known packages).
- Deploying this feature requires a full reindex of all cached versions because code sample keyword scores change.
