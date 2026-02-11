# POJO/DTO Example

Reference for creating DTOs and POJOs in this project.

**Key patterns:**
- Use Lombok `@NoArgsConstructor` / `@AllArgsConstructor` to reduce boilerplate.
- Public fields (no getters/setters) for simple DTOs.
- Use `List` over arrays for collections.

**Source:** `com.fvd.search.services.FileSearchResult`

```java
package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class FileSearchResult {

    public String path;
    public double score;
    public List<String> matchedKeywords;
    public String extension;

}
```
