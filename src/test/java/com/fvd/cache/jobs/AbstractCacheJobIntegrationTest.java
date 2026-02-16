package com.fvd.cache.jobs;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

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
    DataSource dataSource;

    @Inject
    CacheService cacheService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
        cacheService.deleteCache();
    }

    protected List<String> simulateWarmup(String version) {
        List<String> extractedFiles = zipDownloadService.streamAndExtract(version);
        indexService.getOrFetchIndex(version);
        keywordIndexer.build(version, extractedFiles);
        codeSampleIndexer.build(version, extractedFiles);
        return extractedFiles;
    }
}
