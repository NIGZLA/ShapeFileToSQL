package com.nigzla.db;

import com.nigzla.util.SqlIdentifierUtil;

import java.sql.Connection;
import java.sql.Statement;

/**
 * @author NIGZLA
 * 2026/3/4 10:27
 */
public class TableBuilder {
    public void create(Connection conn,
                       String tableName,
                       TableMetadata meta,
                       int srid) throws Exception {

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(SqlIdentifierUtil.quote(tableName))
                .append(" (id serial PRIMARY KEY");

        for (var col : meta.getColumns()) {
            sb.append(", ")
                    .append(SqlIdentifierUtil.quote(col.getName()))
                    .append(" ")
                    .append(col.getSqlType());
        }

        sb.append(")");

        try (Statement st = conn.createStatement()) {
            st.execute(sb.toString());

            st.execute("""
                ALTER TABLE %s
                ADD COLUMN IF NOT EXISTS %s geometry(%s,%d)
                """.formatted(
                            SqlIdentifierUtil.quote(tableName),
                            SqlIdentifierUtil.quote(meta.getGeometryColumn()),
                            meta.getGeometryType(),
                            srid
                    )
            );

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_%s_geom
                ON %s USING GIST (%s)
                """.formatted(
                            tableName,
                            SqlIdentifierUtil.quote(tableName),
                            SqlIdentifierUtil.quote(meta.getGeometryColumn())
                    )
            );
        }
    }

    public String buildCreateTableSql(String tableName,
                                      TableMetadata meta,
                                      int srid) {

        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(SqlIdentifierUtil.quote(tableName))
                .append(" (id serial PRIMARY KEY");

        for (var col : meta.getColumns()) {
            sb.append(", ")
                    .append(SqlIdentifierUtil.quote(col.getName()))
                    .append(" ")
                    .append(col.getSqlType());
        }

        sb.append(");\n");

        sb.append("""
            ALTER TABLE %s
            ADD COLUMN IF NOT EXISTS %s geometry(%s,%d);
            """.formatted(
                SqlIdentifierUtil.quote(tableName),
                SqlIdentifierUtil.quote(meta.getGeometryColumn()),
                meta.getGeometryType(),
                srid
        ));

        sb.append("\n");

        sb.append("""
            CREATE INDEX IF NOT EXISTS idx_%s_geom
            ON %s USING GIST (%s);
            """.formatted(
                tableName,
                SqlIdentifierUtil.quote(tableName),
                SqlIdentifierUtil.quote(meta.getGeometryColumn())
        ));

        sb.append("\n");

        return sb.toString();
    }
}
