package com.fvd.common.utils;

import lombok.experimental.UtilityClass;

/**
 * Utility for filter matching in search operations.
 */
@UtilityClass
public class FilterUtils {

    public static boolean matchesFilter(String filter, String value) {
        return filter == null || filter.isBlank() || filter.equals(value);
    }
}
