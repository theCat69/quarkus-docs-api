package com.fvd.quarkiverse.parser;

public record ResolvedContentSource(
        String org,
        String repo,
        String branch,
        String startPath,
        String extensionName
) {
}
