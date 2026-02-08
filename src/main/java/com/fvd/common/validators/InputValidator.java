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
        if (!version.matches("[a-zA-Z0-9._-]+")) {
            throw new InvalidInputException("version contains invalid characters");
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
}
