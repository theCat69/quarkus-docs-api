package com.fvd.repository.sqlite;

import lombok.experimental.UtilityClass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringJoiner;

/**
 * Utility methods for common SQL operations.
 * <p>
 * Table names used in this utility are hardcoded constants within the calling
 * repository classes — they are NOT user input.
 * </p>
 */
@UtilityClass
public class SqlUtils {

    /**
     * Builds a comma-separated list of SQL placeholders.
     *
     * @param count number of placeholders to generate
     * @return comma-separated placeholders string, or empty string if count &lt;= 0
     */
    public static String buildPlaceholders(int count) {
        if (count <= 0) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    /**
     * Checks if any row exists for a given version in a table.
     * <p>
     * The {@code tableName} and {@code versionColumn} parameters must be hardcoded
     * constants — never user input.
     * </p>
     */
    public static boolean existsByVersion(
            Connection conn, String tableName, String versionColumn, String version)
            throws SQLException {
        String sql = "SELECT 1 FROM " + tableName + " WHERE " + versionColumn + " = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Deletes all rows for a given version from a table.
     * <p>
     * The {@code tableName} and {@code versionColumn} parameters must be hardcoded
     * constants — never user input.
     * </p>
     */
    public static int deleteByVersion(
            Connection conn, String tableName, String versionColumn, String version)
            throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE " + versionColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            return stmt.executeUpdate();
        }
    }
}
