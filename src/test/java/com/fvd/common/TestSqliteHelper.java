package com.fvd.common;

import com.fvd.indexs.stores.SqliteSchemaInitializer;
import lombok.experimental.UtilityClass;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

@UtilityClass
public class TestSqliteHelper {

    /**
     * Creates an SQLiteDataSource pointing to a "test.db" file inside the given directory,
     * initializes the schema, and returns the ready-to-use data source.
     */
    public static SQLiteDataSource createInitializedDataSource(Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
        initializer.initSchema();
        return ds;
    }
}
