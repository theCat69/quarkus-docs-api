package com.fvd.common.validators;

import com.fvd.common.exceptions.InvalidInputException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InputValidator {

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

    public static void validatePath(String path) {
        requireNonEmpty(path, "path");
        if (path.contains("..")) {
            throw new InvalidInputException("path must not contain '..'");
        }
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
}
