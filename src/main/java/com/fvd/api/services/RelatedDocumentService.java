package com.fvd.api.services;

import com.fvd.api.dto.RelatedDocumentRef;
import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.common.utils.DescriptionExtractor;
import com.fvd.common.utils.DocumentTitleExtractor;
import com.fvd.common.utils.FilterUtils;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.search.SearchConfig;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.MetadataAwareSubjectResolver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Service for finding documents related to a given source document
 * using weighted cosine similarity on keyword vectors from the keyword index.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RelatedDocumentService {

    private final SearchService searchService;
    private final DocStore docStore;
    private final MetadataAwareSubjectResolver metadataResolver;
    private final SearchConfig searchConfig;

    /**
     * Finds documents related to the source document at the given path,
     * ranked by cosine similarity of keyword vectors.
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
        KeywordIndex index = searchService.getKeywordIndex(version);
        if (index == null) {
            throw new DocNotFoundException("No keyword index available for version: " + version);
        }

        // Find source document entry
        FileKeywordEntry sourceEntry = null;
        for (FileKeywordEntry file : index.files) {
            if (file.path.equals(sourcePath)) {
                sourceEntry = file;
                break;
            }
        }
        if (sourceEntry == null) {
            throw new DocNotFoundException("Document not found in index: " + sourcePath);
        }

        // Build source keyword vector and original word lookup
        Map<String, Double> sourceVector = buildKeywordVector(sourceEntry);
        Map<String, String> sourceOriginals = buildOriginalWordLookup(sourceEntry);

        double minSimilarity = searchConfig.related().minSimilarity();
        int maxSharedKeywords = searchConfig.related().maxSharedKeywords();

        // Compute similarity against all other documents
        List<CandidateResult> candidates = new ArrayList<>();
        Map<String, DocumentMetadata> metadataMap = metadataResolver.loadMetadataMap(version);
        for (FileKeywordEntry candidate : index.files) {
            if (candidate.path.equals(sourcePath)) {
                continue;
            }

            // Apply filters
            String derivedSubject = metadataResolver.resolveSubject(candidate.path, metadataMap);
            if (!FilterUtils.matchesFilter(subjectFilter, derivedSubject)) {
                continue;
            }
            if (!FilterUtils.matchesFilter(extensionFilter, candidate.extension)) {
                continue;
            }

            Map<String, Double> candidateVector = buildKeywordVector(candidate);
            double similarity = computeCosineSimilarity(sourceVector, candidateVector);

            if (similarity < minSimilarity) {
                continue;
            }

            // Merge original word lookups from source and candidate, preferring longest
            Map<String, String> candidateOriginals = buildOriginalWordLookup(candidate);
            Map<String, String> mergedOriginals = new HashMap<>(sourceOriginals);
            for (Map.Entry<String, String> e : candidateOriginals.entrySet()) {
                mergedOriginals.merge(e.getKey(), e.getValue(),
                        (a, b) -> a.length() >= b.length() ? a : b);
            }

            List<String> shared = extractSharedKeywords(sourceVector, candidateVector,
                    maxSharedKeywords, mergedOriginals);
            candidates.add(new CandidateResult(candidate.path, candidate.extension,
                    derivedSubject, similarity, shared));
        }

        // Sort by similarity descending
        candidates.sort(Comparator.comparingDouble(CandidateResult::similarityScore).reversed());

        int totalCount = candidates.size();
        List<CandidateResult> topN = candidates.subList(0, Math.min(limit, candidates.size()));

        // Enrich with title and description
        List<RelatedDocumentRef> results = new ArrayList<>();
        for (CandidateResult cr : topN) {
            String title = "";
            String description = "";

            Optional<String> contentOpt = docStore.read(version, cr.path());
            if (contentOpt.isPresent()) {
                String content = contentOpt.get();
                title = DocumentTitleExtractor.extractTitle(content);
                description = DescriptionExtractor.extract(content);
            }

            results.add(new RelatedDocumentRef(
                    cr.path(), title, description, cr.subject(), cr.extension(),
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

    Map<String, Double> buildKeywordVector(FileKeywordEntry entry) {
        Map<String, Double> vector = new HashMap<>();
        for (KeywordScore ks : entry.keywords) {
            vector.put(ks.word, (double) ks.score);
        }
        return vector;
    }

    /**
     * Builds a lookup map from stemmed keyword to its best original (un-stemmed) form
     * for a given file keyword entry. When multiple KeywordScores share the same stem,
     * the longest originalWord is kept as it is typically the most descriptive.
     */
    Map<String, String> buildOriginalWordLookup(FileKeywordEntry entry) {
        Map<String, String> lookup = new HashMap<>();
        for (KeywordScore ks : entry.keywords) {
            String existing = lookup.get(ks.word);
            if (existing == null || (ks.originalWord != null && ks.originalWord.length() > existing.length())) {
                lookup.put(ks.word, ks.originalWord != null ? ks.originalWord : ks.word);
            }
        }
        return lookup;
    }

    double computeCosineSimilarity(Map<String, Double> vectorA, Map<String, Double> vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            double a = entry.getValue();
            normA += a * a;
            Double b = vectorB.get(entry.getKey());
            if (b != null) {
                dotProduct += a * b;
            }
        }

        for (double b : vectorB.values()) {
            normB += b * b;
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    List<String> extractSharedKeywords(Map<String, Double> vectorA, Map<String, Double> vectorB,
                                       int maxKeywords,
                                       Map<String, String> originalWordLookup) {
        List<Map.Entry<String, Double>> shared = new ArrayList<>();
        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            Double bValue = vectorB.get(entry.getKey());
            if (bValue != null) {
                shared.add(Map.entry(entry.getKey(), entry.getValue() + bValue));
            }
        }

        // Sort by combined score descending
        shared.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        return shared.stream()
                .limit(maxKeywords)
                .map(e -> originalWordLookup.getOrDefault(e.getKey(), e.getKey()))
                .toList();
    }

    private record CandidateResult(String path, String extension, String subject,
                                   double similarityScore, List<String> sharedKeywords) {
    }
}
