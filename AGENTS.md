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
- PostgreSQL-backed keyword and code-sample indexes.
- Supports quarkiverse extension docs via Antora playbook parsing (Jackson YAML).
- `version` query parameter is optional on all endpoints; defaults to `main`.
- Tests are JUnit 5 with QuarkusTest, RestAssured, AssertJ, Mockito.
- Lombok is available; use it to reduce boilerplate when possible.

## Additional guidelines 
Depending on your mission, identity, role or goal you may need additional guidelines.
ALWAYS read additional guidelines if it is relevant for your mission, identity, role or goal.

### Coding agent
Coding or Orchestrator agents should read documentation here: `.project-guidelines-for-ai/coding`.

### Building the project
Building agents should read documentation here: `.project-guidelines-for-ai/building`.

### Testing the project
Testing agents should read documentation here: `.project-guidelines-for-ai/testing`.

### Updating documentation on the project
Librarian or documentalist agents should read documentation here: `.project-guidelines-for-ai/documentation`.

### Security-reviewer agents
Security reviewer agents should read documentation here: `.project-guidelines-for-ai/security`.