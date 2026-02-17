# Task 03: Create UrlBuilder Utility

> **Dependencies**: None — can start in parallel with any other task.

## Summary

Create a small utility class `UrlBuilder` in `com.fvd.common.utils` that builds canonical URLs for Quarkus guide sections. Given a page name and optional section anchor, it produces `https://quarkus.io/guides/{page}#{section-slug}`. Include a unit test.

## Changes

### `src/main/java/com/fvd/common/utils/UrlBuilder.java` *(created)*

- `@ApplicationScoped` CDI bean
- `String buildUrl(String page, String sectionTitle)` — builds full URL with anchor
- `String buildUrl(String page)` — builds URL without anchor (page-level)
- Section title → anchor slug: lowercase, replace spaces/special chars with `-`, strip leading/trailing `-`
- Strip `.adoc` extension from page name if present

### `src/test/java/com/fvd/common/utils/UrlBuilderTest.java` *(created)*

- Test basic page URL: `"rest-client"` → `https://quarkus.io/guides/rest-client`
- Test section anchor: `"Getting Started"` → `#getting-started`
- Test `.adoc` stripping: `"rest-client.adoc"` → `rest-client`
- Test special character handling in section titles

## Acceptance Criteria

- [ ] `UrlBuilder` is injectable via CDI
- [ ] `buildUrl("rest-client", "Getting Started")` returns `https://quarkus.io/guides/rest-client#getting-started`
- [ ] `.adoc` suffix is stripped from page names
- [ ] Section titles are correctly slugified (lowercase, hyphenated)
- [ ] Unit test passes: `./gradlew test --tests '*UrlBuilderTest'`

## Files

- `src/main/java/com/fvd/common/utils/UrlBuilder.java` *(created)*
- `src/test/java/com/fvd/common/utils/UrlBuilderTest.java` *(created)*
