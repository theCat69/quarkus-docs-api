# Store Example

Reference for creating persistence/IO layer classes in this project.

**Key patterns:**
- `@ApplicationScoped` for CDI singleton lifecycle.
- `@RequiredArgsConstructor` for constructor-based dependency injection.
- Inject services (e.g., `CacheService`) via final fields.
- Use `InputValidator` to validate all user-supplied inputs before filesystem access.
- Return `Optional` when a result may not exist.
- Wrap checked exceptions in `RuntimeException` with a descriptive message.

**Source:** `com.fvd.docs.stores.DocStore`

```java
package com.fvd.docs.stores;

import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
@RequiredArgsConstructor
public class DocStore {

    private final CacheService cacheService;

    public Optional<String> read(String version, String filePath) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(filePath);
        Path docFile = cacheService.versionDir(version).resolve("docs").resolve(filePath);
        if (!Files.exists(docFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(docFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read doc: " + filePath, e);
        }
    }
}
```
