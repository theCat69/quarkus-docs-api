# Feature 10: OpenAPI descriptions for endpoints

Add/complete descriptive text in the existing SmallRye OpenAPI setup to make endpoints discoverable, without full schema/example work.

## Scope and behavior

- Add `@Operation` summaries/descriptions for each REST endpoint.
- Add `@APIResponse` descriptions where currently missing.
- Avoid adding new example bodies or detailed schema annotations unless already present.

## Tasks

- [x] Inventory all REST resources and endpoints.
- [x] Add missing `@Operation` summaries/descriptions.
- [x] Add/complete `@APIResponse` descriptions for existing responses.
- [x] Review generated OpenAPI to ensure descriptions appear.
