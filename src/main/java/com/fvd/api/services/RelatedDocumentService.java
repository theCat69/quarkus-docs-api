package com.fvd.api.services;

import com.fvd.api.dto.RelatedDocumentRef;
import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.common.utils.AsciiDocCleaner;
import com.fvd.common.utils.DocumentTitleExtractor;
import com.fvd.common.utils.FilterUtils;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.search.SearchConfig;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for finding documents related to a given source document
 * using weighted cosine similarity on keyword vectors from the keyword index.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RelatedDocumentService {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^:description:\\s*(.+)$", Pattern.MULTILINE);

    private final SearchService searchService;
    private final DocStore docStore;
    private final SubjectDeriver subjectDeriver;
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

        // Build source keyword vector
        Map<String, Double> sourceVector = buildKeywordVector(sourceEntry);

        double minSimilarity = searchConfig.related().minSimilarity();
        int maxSharedKeywords = searchConfig.related().maxSharedKeywords();

        // Compute similarity against all other documents
        List<CandidateResult> candidates = new ArrayList<>();
        for (FileKeywordEntry candidate : index.files) {
            if (candidate.path.equals(sourcePath)) {
                continue;
            }

            // Apply filters
            String derivedSubject = subjectDeriver.deriveSubject(candidate.path);
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

            List<String> shared = extractSharedKeywords(sourceVector, candidateVector, maxSharedKeywords);
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
                description = extractDescription(content);
            }

            results.add(new RelatedDocumentRef(
                    cr.path(), title, description, cr.subject(), cr.extension(),
                    cr.similarityScore(), cr.sharedKeywords()));
        }

        return RelatedDocumentResponse.builder()
                .results(results)
                .totalCount(totalCount)
                .returnedCount(results.size())
                .build();
    }

    Map<String, Double> buildKeywordVector(FileKeywordEntry entry) {
        Map<String, Double> vector = new HashMap<>();
        for (KeywordScore ks : entry.keywords) {
            vector.put(ks.word, (double) ks.score);
        }
        return vector;
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
                                       int maxKeywords) {
        List<Map.Entry<String, Double>> shared = new ArrayList<>();
        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            Double bValue = vectorB.get(entry.getKey());
            if (bValue != null) {
                shared.add(Map.entry(entry.getKey(), entry.getValue() + bValue));
            }
        }

        // Sort by combined score descending
        shared.sort(Comparator.<Map.Entry<String, Double>, Double>comparing(Map.Entry::getValue).reversed());

        return shared.stream()
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String extractDescription(String content) {
        Matcher matcher = DESCRIPTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return AsciiDocCleaner.clean(matcher.group(1));
        }
        // Fall back to first paragraph after title
        String[] lines = content.split("\n");
        StringBuilder desc = new StringBuilder();
        boolean foundTitle = false;
        for (String line : lines) {
            if (line.startsWith("= ")) {
                foundTitle = true;
                continue;
            }
            if (foundTitle && !line.isBlank() && !line.startsWith(":") && !line.startsWith("=")) {
                if (!desc.isEmpty()) {
                    desc.append(" ");
                }
                desc.append(line.trim());
                if (desc.length() > 200) {
                    break;
                }
            }
            if (foundTitle && line.startsWith("==")) {
                break;
            }
        }
        return AsciiDocCleaner.clean(desc.toString());
    }

    private record CandidateResult(String path, String extension, String subject,
                                   double similarityScore, List<String> sharedKeywords) {
    }
}
