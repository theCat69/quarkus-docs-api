package com.fvd.cache.jobs;

import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

abstract class AbstractCacheJobIntegrationTest {

    @Inject
    ZipDownloadService zipDownloadService;

    @Inject
    IndexService indexService;

    @Inject
    KeywordIndexer keywordIndexer;

    @Inject
    CodeSampleIndexer codeSampleIndexer;

    @Inject
    DocStore docStore;

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    CodeSampleIndexStore codeSampleIndexStore;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
    }

    protected List<String> simulateWarmup(String version) {
        List<String> extractedFiles = zipDownloadService.streamAndExtract(version);
        indexService.getOrFetchIndex(version);
        keywordIndexer.build(version, extractedFiles);
        codeSampleIndexer.build(version, extractedFiles);
        return extractedFiles;
    }
}
