# Feature 58: Fix Subject Classification for Core Docs

> **Dependencies**: None. Independent of Feature 57 (can be implemented in either order).

## Summary

Core Quarkus documentation files are never classified into subjects because all default regex patterns in `SubjectDeriver.loadDefaultPatterns()` require a `/` path separator before the keyword. Core docs are stored with bare filenames like `security-overview.adoc` (no directory prefix), so the pattern `.*/security.*` never matches because `.*/(security)` requires at least one `/` before the keyword. Quarkiverse docs work because they are stored as `quarkiverse/ext-name/file.adoc`. The fix updates all 11 default patterns to match both bare filenames and directory-prefixed paths, **using refined keyword anchors** (e.g., `rest[-.]` instead of `rest.*`) to prevent false positives on prefix-overlapping filenames.

## User Story

As an **AI agent browsing the documentation catalog**, I want core Quarkus documents like `security-overview.adoc`, `rest-json-guide.adoc`, and `getting-started.adoc` to be correctly classified into their respective subjects (security, rest-apis, getting-started) so that I can filter search results by subject and browse documents by category.

## Motivation

### Current Behavior (Broken)

```
SubjectDeriver.deriveSubject("security-overview.adoc")
  → normalizePath → "security-overview.adoc"
  → regex: ".*/security.*".matches("security-overview.adoc") → FALSE (no '/' before 'security')
  → Falls through ALL 11 patterns
  → Returns "misc"
```

Every core Quarkus doc that lacks a directory prefix is classified as `"misc"`, making the subject filter useless for the most important documentation files.

### Why Quarkiverse Docs Work

```
SubjectDeriver.deriveSubject("quarkiverse/quarkus-security-jpa/security-jpa.adoc")
  → normalizePath → "quarkiverse/quarkus-security-jpa/security-jpa.adoc"
  → regex: ".*/security.*".matches("quarkiverse/quarkus-security-jpa/security-jpa.adoc") → TRUE
  → Returns "security"
```

The `/` in `quarkiverse/quarkus-security-jpa/` satisfies the `.*/` prefix in the pattern.

### Root Cause in Code

**File:** `src/main/java/com/fvd/subject/services/SubjectDeriver.java`, lines 77-103

All 11 default patterns (lines 82-92) use the `.*/` prefix:

```java
defaults.put(".*/(getting-started|quickstart|tutorial).*", "getting-started");
defaults.put(".*/cdi.*|.*/lifecycle.*|.*/config(uration)?.*", "core-concepts");
defaults.put(".*/rest.*|.*/resteasy.*|.*/json.*|.*/jaxrs.*", "rest-apis");
defaults.put(".*/hibernate.*|.*/panache.*|.*/datasource.*|.*/database.*|.*/jpa.*|.*/jdbc.*", "data-persistence");
defaults.put(".*/security.*|.*/auth.*|.*/oidc.*|.*/jwt.*|.*/oauth.*|.*/keycloak.*", "security");
defaults.put(".*/kafka.*|.*/amqp.*|.*/messaging.*|.*/reactive-messaging.*", "messaging");
defaults.put(".*/kubernetes.*|.*/openshift.*|.*/docker.*|.*/container.*|.*/cloud.*", "cloud");
defaults.put(".*/metrics.*|.*/health.*|.*/tracing.*|.*/logging.*|.*/opentelemetry.*|.*/micrometer.*", "observability");
defaults.put(".*/test.*|.*/mock.*|.*/junit.*", "testing");
defaults.put(".*/cli.*|.*/dev-services.*|.*/ide.*|.*/maven.*|.*/gradle.*", "tooling");
defaults.put(".*/extension.*|.*/quarkiverse.*", "extensions");
```

The `.*/` requires at least one character followed by `/` before the keyword. Bare filenames have no `/` at all.

---

## Requirements

### R1: Update Default Patterns to Match Bare Filenames (with Refined Keywords)

**File:** `src/main/java/com/fvd/subject/services/SubjectDeriver.java`, method `loadDefaultPatterns()` (lines 77-103)

**Approach:** Two combined changes:

1. **Replace `.*/` prefix with `(^|.*/)` prefix.** This matches either:
   - `^keyword...` (bare filename starting with the keyword)
   - `.*/keyword...` (directory-prefixed path with keyword after a `/`)

2. **Refine ambiguous keywords** by requiring a delimiter (`-`, `.`, or end-of-keyword) after short keywords that are common English prefixes. This prevents false positives where a keyword is a prefix of a longer, unrelated word.

#### False Positive Analysis

The naive `(^|.*/)keyword.*` approach introduces false positives for bare filenames because `^` anchors at the start of the string, so any filename that **starts with** the keyword prefix will match. This was incorrectly dismissed in the prior revision. Concrete examples:

| Pattern keyword | False positive filename | Why it matches | Correct subject |
|----------------|----------------------|---------------|----------------|
| `rest.*` | `restricted-access.adoc` | `restricted` starts with `rest` | `security` (or `misc`) |
| `cli.*` | `client-reference.adoc` | `client` starts with `cli` | `misc` |
| `auth.*` | `auto-config.adoc` | `auto` starts with `aut`... wait, `auth` ≠ `auto`. Actually, `authorization.adoc` is correct. But `authoritative-guide.adoc` could be an edge case — acceptable. | OK in practice |
| `test.*` | `testing-components.adoc` | Correct match — `testing` is about testing | `testing` ✓ |
| `container.*` | `content-negotiation.adoc` | `content` does NOT start with `container` | N/A (no issue) |
| `config.*` | `configuring-something.adoc` | Correct match | `core-concepts` ✓ |

The **confirmed problematic keywords** are:

1. **`rest`** — matches `restricted`, `restore`, `rest-assured` (testing tool, not REST API), etc.
2. **`cli`** — matches `client`, `clipboard`, `cli-tooling` (correct), etc.
3. **`auth`** — low risk in practice (most `auth*` files are security-related), but `auto-*` does NOT match since `auth` ≠ `auto`.

#### Refined Pattern Strategy

For ambiguous keywords, require a delimiter after the keyword:
- `rest[-.]` — matches `rest-client.adoc`, `rest.adoc`, `rest-json-guide.adoc` but NOT `restricted-access.adoc`
- `cli[-.]` — matches `cli-tooling.adoc`, `cli.adoc` but NOT `client-reference.adoc`

For keywords that are already unambiguous (e.g., `security`, `hibernate`, `kafka`, `kubernetes`), no change is needed — they are long enough that accidental prefix overlap is extremely unlikely.

**Updated patterns:**

```java
defaults.put("(^|.*/)(getting-started|quickstart|tutorial).*", "getting-started");
defaults.put("(^|.*/)cdi[-.].*|(^|.*/)lifecycle.*|(^|.*/)config(uration)?[-.].*", "core-concepts");
defaults.put("(^|.*/)rest[-.].*|(^|.*/)resteasy.*|(^|.*/)json[-.].*|(^|.*/)jaxrs.*", "rest-apis");
defaults.put("(^|.*/)hibernate.*|(^|.*/)panache.*|(^|.*/)datasource.*|(^|.*/)database.*|(^|.*/)jpa[-.].*|(^|.*/)jdbc[-.].*", "data-persistence");
defaults.put("(^|.*/)security.*|(^|.*/)auth[-.].*|(^|.*/)oidc.*|(^|.*/)jwt[-.].*|(^|.*/)oauth.*|(^|.*/)keycloak.*", "security");
defaults.put("(^|.*/)kafka.*|(^|.*/)amqp.*|(^|.*/)messaging.*|(^|.*/)reactive-messaging.*", "messaging");
defaults.put("(^|.*/)kubernetes.*|(^|.*/)openshift.*|(^|.*/)docker.*|(^|.*/)container[-.].*|(^|.*/)cloud[-.].*", "cloud");
defaults.put("(^|.*/)metrics.*|(^|.*/)health[-.].*|(^|.*/)tracing.*|(^|.*/)logging.*|(^|.*/)opentelemetry.*|(^|.*/)micrometer.*", "observability");
defaults.put("(^|.*/)test[-.].*|(^|.*/)mock[-.].*|(^|.*/)junit.*", "testing");
defaults.put("(^|.*/)cli[-.].*|(^|.*/)dev-services.*|(^|.*/)ide[-.].*|(^|.*/)maven.*|(^|.*/)gradle.*", "tooling");
defaults.put("(^|.*/)extension.*|(^|.*/)quarkiverse.*", "extensions");
```

#### Delimiter Refinement Summary

Keywords that receive the `[-.]` suffix requirement (short or ambiguous):

| Keyword | Without `[-.]` false positives | With `[-.]` matches correctly |
|---------|-------------------------------|------------------------------|
| `rest` | `restricted`, `restore` | `rest-client`, `rest-json`, `rest.adoc` |
| `cli` | `client`, `clipboard` | `cli-tooling`, `cli.adoc` |
| `auth` | Unlikely, but defensive | `auth-overview`, `auth.adoc` |
| `json` | `jsonb-serialization` (correct), `jsonnet` (unlikely) | `json-binding`, `json.adoc` |
| `jpa` | `jpackage` (unlikely) | `jpa-guide`, `jpa.adoc` |
| `jdbc` | No known issues, but defensive | `jdbc-guide`, `jdbc.adoc` |
| `jwt` | No known issues, but defensive | `jwt-guide`, `jwt.adoc` |
| `cdi` | No known issues, but defensive | `cdi-reference`, `cdi.adoc` |
| `container` | No known issues (long keyword) | `container-image`, `container.adoc` |
| `cloud` | No known issues (long keyword) | `cloud-deployment`, `cloud.adoc` |
| `health` | `healthy-eating` (irrelevant in Quarkus, but defensive) | `health-check`, `health.adoc` |
| `test` | No known Quarkus false positives, but `testament` exists | `test-coverage`, `test.adoc` |
| `mock` | `mockingbird` (irrelevant, but defensive) | `mock-services`, `mock.adoc` |
| `ide` | `idea`, `identity`, `idempotent` | `ide-config`, `ide.adoc` |

### R2: Alternative Simpler Approach (Rejected — Kept for Reference)

An alternative is to simply remove the `.*/` prefix and use `.*keyword.*`:

```java
defaults.put(".*(getting-started|quickstart|tutorial).*", "getting-started");
defaults.put(".*cdi.*|.*lifecycle.*|.*config(uration)?.*", "core-concepts");
// etc.
```

**Trade-off analysis:**

| Approach | Pros | Cons |
|----------|------|------|
| `(^\|.*/)` prefix + `[-.]` suffix | Precise: only matches at path boundaries AND prevents prefix-overlap false positives | Verbose patterns, harder to read |
| `.*keyword.*` prefix | Simple, short patterns | Produces false positives (e.g., `documentation.adoc` matches `.*container.*` due to `contain` substring) |
| `(^\|.*/)keyword.*` (naive, no delimiter) | Moderate precision: matches at path boundaries | Still produces false positives for bare filenames: `restricted-access.adoc` matches `rest.*`, `client-ref.adoc` matches `cli.*` |

**Recommendation:** Use the `(^|.*/)keyword[-.]` approach for ambiguous short keywords. This is the most precise option and avoids all identified false positives while still matching all legitimate Quarkus documentation filenames (which consistently use `-` or `.` delimiters after keywords).

### R3: Preserve Pattern Evaluation Behavior

- Pattern evaluation order must remain unchanged (first match wins)
- Case-insensitive matching must be preserved (controlled by `config.caseInsensitive()`)
- Regex compilation happens at startup (`@PostConstruct`) -- no runtime compilation
- The `normalizePath()` method (line 374-381) is unchanged

### R4: Update Tests for Bare Filename Classification

**File:** `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java`

Add test cases for bare filenames (no directory prefix):

| Input | Expected Subject | Notes |
|-------|-----------------|-------|
| `security-overview.adoc` | `security` | |
| `getting-started.adoc` | `getting-started` | |
| `rest-json-guide.adoc` | `rest-apis` | Matches `rest[-.]` |
| `hibernate-orm.adoc` | `data-persistence` | |
| `config-reference.adoc` | `core-concepts` | Matches `config[-.]` |
| `kafka-guide.adoc` | `messaging` | |
| `kubernetes-deploy.adoc` | `cloud` | |
| `health-check.adoc` | `observability` | Matches `health[-.]` |
| `testing-components.adoc` | `misc` | Does NOT match `test[-.]` (no delimiter after `test`) |
| `test-coverage.adoc` | `testing` | Matches `test[-.]` |
| `cli-tooling.adoc` | `tooling` | Matches `cli[-.]` |
| `extension-development.adoc` | `extensions` | |
| `miscellaneous-topic.adoc` | `misc` | |

### R5: Add False Positive Regression Tests

**File:** `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java`

These test cases explicitly verify that known false-positive-prone filenames are NOT misclassified:

| Input | Expected Subject | Why it's a regression test |
|-------|-----------------|--------------------------|
| `restricted-access.adoc` | `misc` | Must NOT match `rest-apis` — `restricted` starts with `rest` |
| `client-reference.adoc` | `misc` | Must NOT match `tooling` — `client` starts with `cli` |
| `identity-provider.adoc` | `misc` | Must NOT match `tooling` — `identity` starts with `ide` |
| `testing-components.adoc` | `misc` | Must NOT match `testing` — `testing` does not match `test[-.]` (the `i` follows `test`, not `-` or `.`) |
| `auto-deploy.adoc` | `misc` | Must NOT match `security` — `auto` starts with `aut`, not `auth` (not a match even without `[-.]`) |

**Note on `testing-components.adoc`:** With the `[-.]` delimiter approach, `testing-components.adoc` will classify as `misc` because `testing` does not match `test[-.]` (the character after `test` is `i`, not `-` or `.`). This is a **trade-off**: we lose classification of files like `testing-*.adoc` in exchange for preventing false positives on `testament-*.adoc` or similar. If this is unacceptable, the pattern can be expanded to `test(ing)?[-.]` to explicitly allow `testing-` as a variant. **Recommended: use `test(ing)?[-.]`** — this matches `test-*.adoc` and `testing-*.adoc` but not `testament-*.adoc`.

#### Revised Pattern for Testing (with `testing` variant)

```java
defaults.put("(^|.*/)test(ing)?[-.].*|(^|.*/)mock[-.].*|(^|.*/)junit.*", "testing");
```

With this revision:
- `test-coverage.adoc` → `testing` ✓
- `testing-components.adoc` → `testing` ✓
- `testament.adoc` → `misc` ✓

Apply the same `(keyword)(variant)?[-.]` approach to other keywords if needed:
- `auth(entication|orization)?[-.]` — overkill; `auth[-.]` suffices since `auth-*.adoc` covers all Quarkus auth docs.
- `rest(easy)?[-.]` — not needed; `resteasy` already has its own separate keyword.

### R6: Verify Existing Directory-Prefixed Paths Still Work

Ensure that all existing tests for directory-prefixed paths continue to pass. The `(^|.*/)` prefix is a superset of the `.*/` prefix for paths that contain `/`. The added `[-.]` suffix does not affect directory-prefixed paths because Quarkus filenames always use `-` or `.` delimiters (e.g., `rest-client.adoc`, `rest-json-guide.adoc`).

---

## Implementation Notes

### Pattern Verbosity

The `(^|.*/)` approach makes patterns longer, especially for subjects with many alternatives (e.g., `data-persistence` has 6 keywords). Each alternative needs its own `(^|.*/)` prefix because Java regex alternation (`|`) has lower precedence than concatenation:

```java
// WRONG: only `jdbc` gets the prefix
"(^|.*/)hibernate.*|panache.*|datasource.*|database.*|jpa.*|jdbc.*"

// CORRECT: each alternative gets its own prefix
"(^|.*/)hibernate.*|(^|.*/)panache.*|(^|.*/)datasource.*|(^|.*/)database.*|(^|.*/)jpa[-.].*|(^|.*/)jdbc[-.].*"
```

### Delimiter Choice: `[-.]` vs `\\b`

We chose `[-.]` (dash or dot) over `\\b` (word boundary) because:
- Quarkus documentation filenames use `-` as the universal word delimiter (e.g., `rest-client.adoc`, `getting-started.adoc`)
- Files end with `.adoc`, so `.` is the other common delimiter (e.g., `rest.adoc`)
- `\\b` would also match at underscore boundaries and other positions, which is less predictable
- `[-.]` is explicit and easy to reason about

### Path Normalization

`normalizePath()` (line 374-381) converts backslashes to forward slashes and optionally lowercases. After normalization, a bare filename like `Security-Overview.adoc` becomes `security-overview.adoc` (with `caseInsensitive = true`). The patterns are also compiled with `CASE_INSENSITIVE` flag, so both the pattern and the input are case-insensitive.

### Impact on Configured Patterns

This change only affects `loadDefaultPatterns()` (the fallback when `config.patterns()` is null or empty). Users who have configured custom patterns via `application.properties` are unaffected. Their patterns are loaded via `compilePatterns()` (lines 57-75), which is a separate code path.

---

## Tasks

- [ ] Audit all 11 default patterns for false positives using the `(^|.*/)keyword` approach on bare filenames
- [ ] Update all 11 default patterns in `SubjectDeriver.loadDefaultPatterns()` to use `(^|.*/)` prefix instead of `.*/`
- [ ] Refine ambiguous short keywords with `[-.]` suffix: `rest`, `cli`, `auth`, `json`, `jpa`, `jdbc`, `jwt`, `cdi`, `container`, `cloud`, `health`, `test`, `mock`, `ide`
- [ ] Add `test(ing)?[-.]` variant for the testing pattern so `testing-*.adoc` files still match
- [ ] Add parameterized test cases for bare filenames to `SubjectDeriverTest`
- [ ] Add false positive regression tests: `restricted-access.adoc` → `misc`, `client-reference.adoc` → `misc`, `identity-provider.adoc` → `misc`
- [ ] Verify existing parameterized test cases for directory-prefixed paths still pass
- [ ] Run `./gradlew test --tests "com.fvd.subject.*"` -- all subject tests pass
- [ ] Run `./gradlew test` -- full suite passes

---

## Acceptance Criteria

1. `SubjectDeriver.deriveSubject("security-overview.adoc")` returns `"security"` (not `"misc"`)
2. `SubjectDeriver.deriveSubject("getting-started.adoc")` returns `"getting-started"` (not `"misc"`)
3. `SubjectDeriver.deriveSubject("rest-json-guide.adoc")` returns `"rest-apis"` (not `"misc"`)
4. `SubjectDeriver.deriveSubject("config-reference.adoc")` returns `"core-concepts"` (not `"misc"`)
5. All existing directory-prefixed test cases continue to pass (e.g., `quarkiverse/quarkus-oidc/security-oidc.adoc` → `"security"`)
6. `SubjectDeriver.deriveSubject("miscellaneous-topic.adoc")` returns `"misc"` (no false positive match)
7. **`SubjectDeriver.deriveSubject("restricted-access.adoc")` returns `"misc"` (NOT `"rest-apis"`)** — regression test for known false positive
8. **`SubjectDeriver.deriveSubject("client-reference.adoc")` returns `"misc"` (NOT `"tooling"`)** — regression test for known false positive
9. **`SubjectDeriver.deriveSubject("identity-provider.adoc")` returns `"misc"` (NOT `"tooling"`)** — regression test for known false positive
10. `SubjectDeriver.deriveSubject("testing-components.adoc")` returns `"testing"` (via `test(ing)?[-.]` pattern)
11. Only `loadDefaultPatterns()` is modified; `compilePatterns()` and `deriveSubject()` logic are unchanged
12. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **`[-.]` suffix is too restrictive — rejects legitimate filenames that don't use `-` or `.` after the keyword** | Low | Medium | Quarkus docs uniformly use `-` as a word separator. Verify against actual `quarkusio.github.io` filenames before merge. If edge cases are found, add explicit keyword variants (e.g., `test(ing)?[-.]`). |
| **`rest[-.]` still misses `resting-*.adoc` or similar** | Very Low | Low | No known Quarkus docs use such filenames. The `[-.]` approach is correct for the documented corpus. |
| **False positives from bare filenames when using `(^|.*/)` without `[-.]`** | **Confirmed** | **Medium** | **This is the primary risk this spec addresses.** Without `[-.]`, `restricted-access.adoc` matches `rest.*` at `^`, `client-reference.adoc` matches `cli.*` at `^`, and `identity-provider.adoc` matches `ide.*` at `^`. The `[-.]` suffix prevents all three. |
| Regex performance regression from `(^|.*/)` vs `.*/` | Very Low | Low | Both are simple patterns; the `^` branch short-circuits for bare filenames |
| Existing configured patterns in `application.properties` are NOT updated | N/A | None | This change only affects default patterns; configured patterns are the user's responsibility |
| Pattern verbosity makes maintenance harder | Low | Low | Consider extracting a helper method like `patternPrefix(String keyword)` that generates `(^|.*/)keyword[-.]` patterns programmatically |
| **`test(ing)?[-.]` pattern doesn't match `test.adoc`** | Very Low | Low | `test.adoc` matches because `.` satisfies `[-.]`. Only `test` with no suffix at all would fail — but `.adoc` extension means the full filename is always `test.adoc` or `test-something.adoc`. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Audit all patterns for false positives | 0.5 |
| Update 11 default patterns in `loadDefaultPatterns()` with `(^|.*/)` prefix and `[-.]` refinements | 0.75 |
| Add parameterized test cases for bare filenames | 0.5 |
| Add false positive regression tests | 0.25 |
| Test edge cases and verify | 0.5 |
| Run full test suite | 0.25 |
| **Total** | **~2.75 hours** |

---

## Files Modified

### Production Code (1 file)
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` -- update `loadDefaultPatterns()` method (lines 77-103)

### Test Code (1 file)
- `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java` -- add bare-filename test cases + false positive regression tests

---

END OF FILE
