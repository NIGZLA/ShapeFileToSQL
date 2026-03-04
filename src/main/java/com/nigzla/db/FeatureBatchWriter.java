package com.nigzla.db;

import com.nigzla.shp.ShapefileContext;
import com.nigzla.util.SqlIdentifierUtil;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.io.WKTWriter;
import org.opengis.feature.simple.SimpleFeature;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * @author NIGZLA
 * 2026/3/4 10:56
 */
public class FeatureBatchWriter {
    private static final int BATCH_SIZE = 500;

    public void write(Connection conn,
                      String tableName,
                      TableMetadata meta,
                      ShapefileContext context,
                      int srid) throws Exception {

        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();

        for (var col : meta.getColumns()) {
            if (!cols.isEmpty()) {
                cols.append(",");
                vals.append(",");
            }
            cols.append(SqlIdentifierUtil.quote(col.getName()));
            vals.append("?");
        }

        cols.append(",").append(SqlIdentifierUtil.quote(meta.getGeometryColumn()));
        vals.append(",ST_GeomFromText(?,").append(srid).append(")");

        String sql = "INSERT INTO " +
                SqlIdentifierUtil.quote(tableName) +
                "(" + cols + ") VALUES (" + vals + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             SimpleFeatureIterator it = context.getCollection().features()) {

            WKTWriter wktWriter = new WKTWriter();
            int count = 0;

            while (it.hasNext()) {
                SimpleFeature f = it.next();
                int index = 1;

                for (var col : meta.getColumns()) {
                    ps.setObject(index++, f.getAttribute(col.getName()));
                }

                Object geom = f.getAttribute(meta.getGeometryColumn());
                String wkt = geom == null ? null :
                        wktWriter.write((org.locationtech.jts.geom.Geometry) geom);

                ps.setString(index, wkt);

                ps.addBatch();
                count++;

                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }
    }

    private String buildInsertTemplate(String tableName,
                                       TableMetadata meta,
                                       int srid) {

        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();

        for (var col : meta.getColumns()) {
            if (!cols.isEmpty()) {
                cols.append(",");
                vals.append(",");
            }
            cols.append(SqlIdentifierUtil.quote(col.getName()));
            vals.append("?");
        }

        cols.append(",").append(SqlIdentifierUtil.quote(meta.getGeometryColumn()));
        vals.append(",ST_GeomFromText(?,").append(srid).append(")");

        return "INSERT INTO " +
                SqlIdentifierUtil.quote(tableName) +
                "(" + cols + ") VALUES (" + vals + ")";
    }

    // 新增：生成完整 INSERT SQL（用于 -t）
    public String buildInsertSql(String tableName,
                                 TableMetadata meta,
                                 ShapefileContext context,
                                 int srid) throws Exception {

        StringBuilder result = new StringBuilder();

        WKTWriter wktWriter = new WKTWriter();

        try (SimpleFeatureIterator it = context.getCollection().features()) {

            while (it.hasNext()) {

                SimpleFeature f = it.next();

                result.append("INSERT INTO ")
                        .append(SqlIdentifierUtil.quote(tableName))
                        .append(" (");

                StringBuilder cols = new StringBuilder();
                StringBuilder vals = new StringBuilder();

                for (var col : meta.getColumns()) {

                    if (!cols.isEmpty()) {
                        cols.append(",");
                        vals.append(",");
                    }

                    cols.append(SqlIdentifierUtil.quote(col.getName()));

                    Object val = f.getAttribute(col.getName());
                    vals.append(val == null ? "NULL" : "'" + val.toString().replace("'", "''") + "'");
                }

                Object geom = f.getAttribute(meta.getGeometryColumn());
                String wkt = geom == null ? null :
                        wktWriter.write((org.locationtech.jts.geom.Geometry) geom);

                cols.append(",").append(SqlIdentifierUtil.quote(meta.getGeometryColumn()));
                vals.append(",ST_GeomFromText(")
                        .append(wkt == null ? "NULL" : "'" + wkt + "'")
                        .append(",").append(srid).append(")");

                result.append(cols)
                        .append(") VALUES (")
                        .append(vals)
                        .append(");\n");
            }
        }

        return result.toString();
    }
}
