package com.fvd.repository.sqlite;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlUtilsTest {

    // --- buildPlaceholders tests ---

    @Test
    void buildPlaceholdersReturnsEmptyStringForZero() {
        assertThat(SqlUtils.buildPlaceholders(0)).isEmpty();
    }

    @Test
    void buildPlaceholdersReturnsEmptyStringForNegative() {
        assertThat(SqlUtils.buildPlaceholders(-1)).isEmpty();
    }

    @Test
    void buildPlaceholdersReturnsSinglePlaceholder() {
        assertThat(SqlUtils.buildPlaceholders(1)).isEqualTo("?");
    }

    @Test
    void buildPlaceholdersReturnsMultiplePlaceholders() {
        assertThat(SqlUtils.buildPlaceholders(3)).isEqualTo("?, ?, ?");
    }

    // --- existsByVersion tests ---

    @Test
    void existsByVersionReturnsTrueWhenRowExists() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);

        boolean result = SqlUtils.existsByVersion(conn, "files", "version", "main");

        assertThat(result).isTrue();
        verify(stmt).setString(1, "main");
    }

    @Test
    void existsByVersionReturnsFalseWhenNoRowExists() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        boolean result = SqlUtils.existsByVersion(conn, "github_index", "version", "3.27");

        assertThat(result).isFalse();
        verify(stmt).setString(1, "3.27");
    }

    @Test
    void existsByVersionUsesCorrectSql() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        SqlUtils.existsByVersion(conn, "code_samples", "version", "main");

        verify(conn).prepareStatement("SELECT 1 FROM code_samples WHERE version = ? LIMIT 1");
    }

    // --- deleteByVersion tests ---

    @Test
    void deleteByVersionReturnsAffectedRowCount() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(5);

        int result = SqlUtils.deleteByVersion(conn, "files", "version", "main");

        assertThat(result).isEqualTo(5);
        verify(stmt).setString(1, "main");
    }

    @Test
    void deleteByVersionReturnsZeroWhenNoRows() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        int result = SqlUtils.deleteByVersion(conn, "github_index", "version", "unknown");

        assertThat(result).isEqualTo(0);
    }

    @Test
    void deleteByVersionUsesCorrectSql() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        SqlUtils.deleteByVersion(conn, "code_samples", "version", "3.20");

        verify(conn).prepareStatement("DELETE FROM code_samples WHERE version = ?");
        verify(stmt).setString(1, "3.20");
    }
}
