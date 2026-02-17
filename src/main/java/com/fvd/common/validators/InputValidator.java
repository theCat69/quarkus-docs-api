package com.fvd.common.validators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;

import com.fvd.common.StopWords;
import com.fvd.common.exceptions.InvalidInputException;

@UtilityClass
public class InputValidator {

    public static final String DEFAULT_VERSION = "main";
    public static final int MAX_QUERY_LENGTH = 500;
    public static final int MAX_FILTER_LENGTH = 200;

    public static String resolveVersion(String version) {
        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }
        validateVersion(version);
        return version;
    }

    public static void requireNonEmpty(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(paramName + " must not be empty");
        }
    }

    public static void validateVersion(String version) {
        requireNonEmpty(version, "version");
        if (!version.matches("[a-zA-Z0-9._/-]+")) {
            throw new InvalidInputException("version contains invalid characters");
        }
        if (version.contains("..")) {
            throw new InvalidInputException("version must not contain '..'");
        }
    }

    public static void validateVersionExists(String version, List<String> cachedVersions) {
        if (DEFAULT_VERSION.equals(version)) {
            return; // main is always accepted
        }
        if (!cachedVersions.contains(version)) {
            List<String> allVersions = new ArrayList<>(cachedVersions);
            if (!allVersions.contains(DEFAULT_VERSION)) {
                allVersions.addFirst(DEFAULT_VERSION);
            }
            String available = String.join(", ", allVersions);
            throw new InvalidInputException(
                    "Unknown version '" + version + "'. Available versions: " + available);
        }
    }

    public static void validateSubjectExists(String subject, Set<String> validSubjects) {
        if (subject == null || subject.isBlank()) {
            return; // null/blank means no filter, always valid
        }
        if (!validSubjects.contains(subject)) {
            String available = String.join(", ", validSubjects.stream().sorted().toList());
            throw new InvalidInputException(
                    "Unknown subject '" + subject + "'. Available subjects: " + available);
        }
    }

    public static void validatePath(String path) {
        requireNonEmpty(path, "path");
        if (path.startsWith("/")) {
            throw new InvalidInputException("Path must not be absolute");
        }
        if (path.contains("..")) {
            throw new InvalidInputException("path must not contain '..'");
        }
    }

    public static List<String> validateBatchPaths(List<String> paths, int maxBatchSize) {
        if (paths == null || paths.isEmpty()) {
            throw new InvalidInputException("paths must not be empty");
        }
        if (paths.size() > maxBatchSize) {
            throw new InvalidInputException("paths must not exceed " + maxBatchSize + " entries");
        }
        List<String> deduplicated = paths.stream()
                .distinct()
                .toList();
        for (String path : deduplicated) {
            validatePath(path);
        }
        return deduplicated;
    }

    public static void validateFilePaths(String filePaths) {
        requireNonEmpty(filePaths, "filePaths");
        for (String path : filePaths.split(",")) {
            String trimmed = path.trim();
            if (trimmed.isEmpty()) {
                throw new InvalidInputException("filePaths contains an empty entry");
            }
            if (trimmed.contains("..")) {
                throw new InvalidInputException("filePaths must not contain '..'");
            }
        }
    }

    public static void validateKeywords(String keywords) {
        requireNonEmpty(keywords, "keywords");
    }

    public static List<String> parseKeywords(String raw) {
        requireNonEmpty(raw, "keywords");
        List<String> filtered = Arrays.stream(raw.trim().split("\\s+"))
                .map(String::toLowerCase)
                .filter(k -> !StopWords.DEFAULT.contains(k))
                .toList();
        if (filtered.isEmpty()) {
            throw new InvalidInputException("All keywords are stop words");
        }
        return filtered;
    }

    public static void validateSectionTitle(String sectionTitle) {
        requireNonEmpty(sectionTitle, "sectionTitle");
    }

    public static int validateLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null) {
            return defaultLimit;
        }
        if (limit < 1) {
            throw new InvalidInputException("limit must be at least 1");
        }
        if (limit > maxLimit) {
            throw new InvalidInputException("limit must not exceed " + maxLimit);
        }
        return limit;
    }

    public static int validateOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new InvalidInputException("offset must not be negative");
        }
        return offset;
    }

    public static void validateQueryLength(String query) {
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidInputException("q must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
    }

    public static String normalizeAndValidateFilter(String filter, String paramName) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        String trimmed = filter.trim();
        if (trimmed.length() > MAX_FILTER_LENGTH) {
            throw new InvalidInputException(paramName + " must not exceed " + MAX_FILTER_LENGTH + " characters");
        }
        if (!trimmed.matches("[a-zA-Z0-9.:_-]+")) {
            throw new InvalidInputException(paramName + " contains invalid characters");
        }
        return trimmed;
    }
}
