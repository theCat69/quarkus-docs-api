package com.fvd.api.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fvd.api.dto.RelatedDocumentRef;
import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;
import com.fvd.search.SearchConfig;

/**
 * Service for finding documents related to a given source document
 * using topic and extension overlap similarity from the doc_chunks index.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RelatedDocumentService {

    private final DocChunkStore docChunkStore;
    private final SearchConfig searchConfig;

    /**
     * Finds documents related to the source document at the given path,
     * ranked by overlap of shared topics and extensions.
     *
     * @param version         the documentation version
     * @param sourcePath      path of the source document
     * @param subjectFilter   optional subject filter
     * @param extensionFilter optional extension filter
     * @param limit           maximum number of results to return
     * @return paginated response with related document references
     */
    public RelatedDocumentResponse findRelatedDocuments(String version, String sourcePath,
                                                        String subjectFilter, String extensionFilter,
                                                        int limit) {
        String sourcePage = sourcePath.endsWith(".adoc")
                ? sourcePath.substring(0, sourcePath.length() - 5)
                : sourcePath;

        List<ChunkSearchRow> sourceChunks = docChunkStore.findByPage(version, sourcePage);
        if (sourceChunks.isEmpty()) {
            throw new DocNotFoundException("Document not found in index: " + sourcePath);
        }

        // Collect all topics and extensions from source chunks (union across all chunks)
        Set<String> sourceTopics = new HashSet<>();
        Set<String> sourceExtensions = new HashSet<>();
        for (ChunkSearchRow chunk : sourceChunks) {
            if (chunk.topics() != null) {
                sourceTopics.addAll(chunk.topics());
            }
            if (chunk.extensions() != null) {
                sourceExtensions.addAll(chunk.extensions());
            }
        }

        if (sourceTopics.isEmpty() && sourceExtensions.isEmpty()) {
            return RelatedDocumentResponse.builder()
                    .results(List.of())
                    .totalCount(0)
                    .returnedCount(0)
                    .offset(0)
                    .limit(limit)
                    .hasMore(false)
                    .build();
        }

        // Get more than needed for post-query filtering
        int fetchLimit = (int) Math.min((long) limit * 3, 100);
        List<DocChunkStore.RelatedPageRow> candidates = docChunkStore.findRelatedPages(
                version, sourcePage,
                new ArrayList<>(sourceTopics),
                new ArrayList<>(sourceExtensions),
                fetchLimit);

        double minSimilarity = searchConfig.related().minSimilarity();
        int maxSharedKeywords = searchConfig.related().maxSharedKeywords();
        int totalSourceTags = sourceTopics.size() + sourceExtensions.size();

        // Compute overlap score and filter
        List<CandidateResult> scoredCandidates = new ArrayList<>();
        for (DocChunkStore.RelatedPageRow candidate : candidates) {
            // Apply subject filter
            if (subjectFilter != null && !subjectFilter.isEmpty()) {
                if (candidate.topics() == null || !candidate.topics().contains(subjectFilter)) {
                    continue;
                }
            }

            // Apply extension filter
            if (extensionFilter != null && !extensionFilter.isEmpty()) {
                if (candidate.extensions() == null || !candidate.extensions().contains(extensionFilter)) {
                    continue;
                }
            }

            // Compute overlap score: shared topics + shared extensions
            List<String> sharedTopics = new ArrayList<>();
            if (candidate.topics() != null) {
                for (String topic : candidate.topics()) {
                    if (sourceTopics.contains(topic)) {
                        sharedTopics.add(topic);
                    }
                }
            }

            int sharedExtensionCount = 0;
            if (candidate.extensions() != null) {
                for (String ext : candidate.extensions()) {
                    if (sourceExtensions.contains(ext)) {
                        sharedExtensionCount++;
                    }
                }
            }

            int overlapCount = sharedTopics.size() + sharedExtensionCount;
            double normalizedScore = totalSourceTags > 0
                    ? (double) overlapCount / totalSourceTags
                    : 0.0;

            if (normalizedScore < minSimilarity) {
                continue;
            }

            // Cap shared topics at maxSharedKeywords for the sharedKeywords field
            List<String> cappedSharedTopics = sharedTopics.size() > maxSharedKeywords
                    ? sharedTopics.subList(0, maxSharedKeywords)
                    : sharedTopics;

            String candidateSubject = (candidate.topics() != null && !candidate.topics().isEmpty())
                    ? candidate.topics().get(0) : null;
            String candidateExtension = (candidate.extensions() != null && !candidate.extensions().isEmpty())
                    ? candidate.extensions().get(0) : null;

            scoredCandidates.add(new CandidateResult(
                    candidate.page() + ".adoc",
                    candidate.title(),
                    candidate.summary(),
                    candidateSubject,
                    candidateExtension,
                    normalizedScore,
                    cappedSharedTopics));
        }

        // Sort by overlap score descending
        scoredCandidates.sort(Comparator.comparingDouble(CandidateResult::similarityScore).reversed());

        int totalCount = scoredCandidates.size();
        List<CandidateResult> topN = scoredCandidates.subList(0, Math.min(limit, scoredCandidates.size()));

        List<RelatedDocumentRef> results = new ArrayList<>();
        for (CandidateResult cr : topN) {
            results.add(new RelatedDocumentRef(
                    cr.path(), cr.title(),
                    cr.description() != null ? cr.description() : "",
                    cr.subject(), cr.extension(),
                    cr.similarityScore(), cr.sharedKeywords()));
        }

        return RelatedDocumentResponse.builder()
                .results(results)
                .totalCount(totalCount)
                .returnedCount(results.size())
                .offset(0)
                .limit(limit)
                .hasMore(results.size() < totalCount)
                .build();
    }

    private record CandidateResult(String path, String title, String description,
                                   String subject, String extension,
                                   double similarityScore, List<String> sharedKeywords) {
    }
}
