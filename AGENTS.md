# AGENTS.md

This file guides agentic coding in this repository. Follow it exactly.

## Critical rules
** CRITICAL RULES ** 
Those RULES are CRITICAL you must follow them.
- ALWAYS use the question tool to interact with the user 
- NEVER return directly unless you can't use the question tool or if ALL tasks and features are done, reviewed and accepted.
** END CRITICAL RULES **

## Project summary
- Quarkus REST API using Gradle (wrapper only), with OpenAPI annotations.
- Java 21 source/target compatibility.
- Sources docs from the `quarkusio.github.io` website repository (not the Quarkus source repo).
- Caches Quarkus docs by version and provides search across keyword, section, and code-sample indexes.
- SQLite-backed keyword and code-sample indexes.
- Supports quarkiverse extension docs via Antora playbook parsing (Jackson YAML).
- `version` query parameter is optional on all endpoints; defaults to `main`.
- Tests are JUnit 5 with QuarkusTest, RestAssured, AssertJ, Mockito.
- Lombok is available; use it to reduce boilerplate when possible.

## Repository rules
- No Cursor rules found in `.cursor/rules/` or `.cursorrules`.
- No Copilot rules found in `.github/copilot-instructions.md`.
