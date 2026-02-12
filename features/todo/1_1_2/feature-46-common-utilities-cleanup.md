# Feature 46: Common Utilities & Exception Handling Cleanup

## Summary

Consolidate duplicated patterns across common utilities including exception mappers, extension path mapping, zip extraction, and file utilities.

## User Story

**As a** maintainer  
**I want** duplicated utility code consolidated  
**So that** the codebase is easier to maintain and extend

## Motivation

~150 lines of duplicated code across 5 patterns:
- 4 exception mappers with identical structure (~80 lines)
- 2 cache jobs with identical `buildExtensionMap()` (~40 lines)
- 2 services with similar zip extraction patterns (~60 lines)
- 1 service with manual directory cleanup (~25 lines)
- 2 parsers with identical regex patterns (~4 lines)

---

## Requirements

### 1. Abstract Exception Mapper Base Class

```java
package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.ExceptionMapper;

public abstract class AbstractProblemDetailMapper<T extends Throwable> 
        implements ExceptionMapper<T> {

    @Context
    UriInfo uriInfo;

    @Override
    public final Response toResponse(T exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        ProblemDetail problem = ProblemDetail.of(
                getStatus().getStatusCode(),
                getTitle(),
                getDetail(exception),
                instance
        );
        return Response.status(getStatus())
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    protected abstract Response.Status getStatus();
    protected abstract String getTitle();
    protected abstract String getDetail(T exception);
}
```

**Refactored mappers:**

```java
@Provider
public class InvalidInputExceptionMapper 
        extends AbstractProblemDetailMapper<InvalidInputException> {
    protected Response.Status getStatus() { return Response.Status.BAD_REQUEST; }
    protected String getTitle() { return "Bad Request"; }
    protected String getDetail(InvalidInputException e) { return e.getMessage(); }
}
```

### 2. Extension Path Utilities

```java
package com.fvd.common.utils;

import lombok.experimental.UtilityClass;
import java.util.*;

@UtilityClass
public class ExtensionPathUtils {

    public static final String CORE_EXTENSION_KEY = "quarkus-core";
    public static final String QUARKIVERSE_PREFIX = "quarkiverse/";

    public static Map<String, List<String>> groupByExtension(List<String> allFiles) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<String> coreFiles = new ArrayList<>();
        Map<String, List<String>> quarkiverseGroups = new LinkedHashMap<>();

        for (String path : allFiles) {
            if (path.startsWith(QUARKIVERSE_PREFIX)) {
                String ext = extractExtensionName(path);
                if (ext != null) {
                    quarkiverseGroups.computeIfAbsent(ext, k -> new ArrayList<>()).add(path);
                }
            } else {
                coreFiles.add(path);
            }
        }

        result.put(CORE_EXTENSION_KEY, coreFiles);
        result.putAll(quarkiverseGroups);
        return result;
    }

    static String extractExtensionName(String path) {
        String[] parts = path.split("/", 3);
        return parts.length >= 2 ? parts[1] : null;
    }
}
```

### 3. File Utilities

```java
package com.fvd.common.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
@UtilityClass
public class FileUtils {

    public static void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to delete directory: {}", directory, e);
        }
    }
}
```

### 4. Zip Stream Processor

```java
package com.fvd.common.utils;

import lombok.experimental.UtilityClass;
import java.io.*;
import java.util.function.*;
import java.util.zip.*;

@UtilityClass
public class ZipStreamProcessor {

    public static void processEntries(
            InputStream zipStream,
            Predicate<String> filter,
            BiConsumer<String, byte[]> processor) throws IOException {
        
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                String entryName = entry.getName();
                if (filter.test(entryName)) {
                    processor.accept(entryName, zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
    }
}
```

---

## Tasks

- [ ] Create `AbstractProblemDetailMapper<T>` base class
- [ ] Refactor 4 exception mappers to extend base
- [ ] Create `ExtensionPathUtils` utility
- [ ] Remove `buildExtensionMap()` from cache jobs
- [ ] Create `FileUtils.deleteDirectoryQuietly()`
- [ ] Refactor `ZipDownloadService` to use utility
- [ ] Create `ZipStreamProcessor` (optional)
- [ ] Write unit tests

---

## Acceptance Criteria

- [ ] `AbstractProblemDetailMapper<T>` exists with template methods
- [ ] All 4 mappers extend base class
- [ ] `ExtensionPathUtils.groupByExtension()` replaces duplicate methods
- [ ] `FileUtils.deleteDirectoryQuietly()` replaces manual cleanup
- [ ] All existing tests pass
- [ ] No public API changes

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Behavioral change in mappers | High | Verify identical output in tests |
| Zip processing edge cases | Medium | Test with real archives |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Abstract Exception Mapper | 1-2 |
| Extension Path Utilities | 1 |
| File Utilities | 0.5 |
| Zip Stream Processor | 1-2 |
| Testing | 2-3 |
| **Total** | **6-9** |

---

END OF FILE
