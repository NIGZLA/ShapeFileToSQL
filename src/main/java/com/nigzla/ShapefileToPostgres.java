package com.nigzla;

import java.util.Date;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.referencing.CRS;
import org.locationtech.jts.io.WKTWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 1)将 Shapefile 压缩包解压到临时目录
 * 2)使用 GeoTools 读取 Shapefile
 * 3)在 Postgres（PostGIS）中创建数据表，并将要素（features）插入
 * @用法 java -jar shp2pg.jar [压缩包路径] [表名] [数据库主机] [数据库端口] [数据库名] [数据库用户名] [数据库密码] [SRID] [字符集]
 * @Usage: java -jar shp2pg.jar [zip-path] [table-name] [host] [port] [database] [db-user] [db-pass] [srid] [charset]
 * @author NIGZLA
 */
public class ShapefileToPostgres {

    // JDBC插入的批处理大小
    private static final int BATCH_SIZE = 500;

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println(
                    "Usage: java -jar shp2pg.jar <zip-path> <table-name> <host> <port> <database> <db-user> <db-pass> [srid] [charset]");
            System.out.println("Example: java -jar shp2pg.jar data.zip my_table localhost 5432 mydb user pass 4326 UTF-8");
            System.out.println("Default SRID is 4326, default charset is UTF-8");
            System.exit(1);
        }

        String zipPath = args[0];
        String tableName = args[1];
        String dbHost = args[2];
        String dbPort = args[3];
        String dbName = args[4];
        String dbUser = args[5];
        String dbPass = args[6];

        Integer forceSrid = null;
        String charset = "UTF-8";

        if (args.length >= 8) {
            try {
                forceSrid = Integer.parseInt(args[7]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid SRID provided. Using default or auto-detected value.");
            }
        }

        if (args.length >= 9) {
            charset = args[8];
        }

        // 自动构建JDBC URL（移除charset参数，通过连接后设置）
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?currentSchema=public&ApplicationName=ShapefileImporter",
                dbHost, dbPort, dbName);

        System.out.println("Connecting to database: " + jdbcUrl);
        System.out.println("Using character set: " + charset);

        Path tempDir = Files.createTempDirectory("shp_unzip_");
        try {
            unzip(zipPath, tempDir);
            Optional<Path> shp = findFileWithExtension(tempDir, ".shp");
            if (shp.isEmpty()) {
                throw new FileNotFoundException("No .shp file found in the zip archive");
            }
            System.out.println("Found shapefile: " + shp.get());

            // 打开 shapefile 数据存储
            Map<String, Object> map = new HashMap<>();
            map.put("url", shp.get().toUri().toURL());
            map.put("charset", charset);
            DataStore dataStore = DataStoreFinder.getDataStore(map);
            if (dataStore == null) {
                // Try ShapefileDataStore directly
                ShapefileDataStore sds = new ShapefileDataStore(shp.get().toUri().toURL());
                sds.setCharset(java.nio.charset.Charset.forName(charset));
                dataStore = sds;
            }

            String typeName = dataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = dataStore.getFeatureSource(
                    typeName);
            SimpleFeatureCollection collection = (SimpleFeatureCollection) featureSource.getFeatures();
            SimpleFeatureTypeImpl schema = (SimpleFeatureTypeImpl) collection.getSchema();

            // Determine SRID
            int srid = 4326;
            if (forceSrid != null) {
                srid = forceSrid;
            } else {
                CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
                if (crs != null) {
                    Integer code = null;
                    try {
                        // Try to lookup EPSG code
                        String epsg = CRS.lookupIdentifier(crs, true);
                        if (epsg != null && epsg.toUpperCase().startsWith("EPSG:")) {
                            code = Integer.parseInt(epsg.split(":")[1]);
                        }
                    } catch (Exception ignored) {
                    }
                    if (code != null) {
                        srid = code;
                    }
                }
            }

            System.out.println("Using SRID: " + srid);

            // Build column definitions
            List<AttributeDescriptor> descriptors = schema.getAttributeDescriptors();
            String geomColumn = null;
            List<ColumnDef> columns = new ArrayList<>();
            for (AttributeDescriptor ad : descriptors) {
                String name = ad.getLocalName();
                Class<?> binding = ad.getType().getBinding();

                // detect geometry descriptor
                if (ad instanceof GeometryDescriptor
                        || org.locationtech.jts.geom.Geometry.class.isAssignableFrom(binding)) {
                    geomColumn = name;
                    continue;
                }

                String sqlType = mapJavaClassToSqlType(binding);
                columns.add(new ColumnDef(name, sqlType));
            }

            if (geomColumn == null) {
                throw new IllegalStateException("No geometry field found in shapefile schema");
            }

            // Create table
            try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
                // 验证并设置字符集
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW client_encoding")) {
                    if (rs.next()) {
                        String currentEncoding = rs.getString(1);
                        System.out.println("Current client encoding: " + currentEncoding);

                        if (!"UTF8".equalsIgnoreCase(currentEncoding)) {
                            stmt.execute("SET client_encoding = 'UTF8'");
                            stmt.execute("SET NAMES 'UTF8'");
                            System.out.println("Client encoding changed to UTF8");
                        } else {
                            System.out.println("Client encoding is already UTF8");
                        }
                    }
                }

                conn.setAutoCommit(false);
                createTableIfNotExists(conn, tableName, columns, geomColumn);
                insertFeatures(conn, tableName, columns, geomColumn, collection, srid, charset);
                conn.commit();
                System.out.println("Import finished successfully.");
            }

            dataStore.dispose();

        } finally {
            // cleanup temp dir
            try {
                deleteRecursively(tempDir);
            } catch (IOException e) {
                System.err.println("Failed to delete temp dir: " + e.getMessage());
            }
        }
    }

    private static void unzip(String zipPath, Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(Paths.get(zipPath));
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private static Optional<Path> findFileWithExtension(Path dir, String ext) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    Optional<Path> rec = findFileWithExtension(p, ext);
                    if (rec.isPresent()) {
                        return rec;
                    }
                } else {
                    if (p.getFileName().toString().toLowerCase().endsWith(ext)) {
                        return Optional.of(p);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String mapJavaClassToSqlType(Class<?> c) {
        if (c == null) {
            return "text";
        }
        if (String.class.isAssignableFrom(c)) {
            return "text";
        }
        if (Integer.class.isAssignableFrom(c) || int.class.isAssignableFrom(c)
                || Short.class.isAssignableFrom(c) || short.class.isAssignableFrom(c)) {
            return "integer";
        }
        if (Long.class.isAssignableFrom(c) || long.class.isAssignableFrom(c)) {
            return "bigint";
        }
        if (Double.class.isAssignableFrom(c) || double.class.isAssignableFrom(c)
                || Float.class.isAssignableFrom(c) || float.class.isAssignableFrom(c)) {
            return "double precision";
        }
        if (Boolean.class.isAssignableFrom(c) || boolean.class.isAssignableFrom(c)) {
            return "boolean";
        }
        if (java.util.Date.class.isAssignableFrom(c) || java.sql.Timestamp.class.isAssignableFrom(c)
                || java.sql.Date.class.isAssignableFrom(c)) {
            return "timestamp";
        }
        // fallback
        return "text";
    }

    private static void createTableIfNotExists(Connection conn, String tableName,
                                               List<ColumnDef> columns, String geomColumn) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quoteIdent(tableName)).append(" (");
        sb.append("id serial PRIMARY KEY");
        for (ColumnDef col : columns) {
            sb.append(", ").append(quoteIdent(col.name)).append(" ").append(col.sqlType);
        }
        sb.append(")");
        try (Statement st = conn.createStatement()) {
            st.execute(sb.toString());
            // add geometry column using PostGIS helper (safe: will ignore if exists)
            String geomType = "geometry"; // generic geometry type
            String alter =
                    "ALTER TABLE " + quoteIdent(tableName) + " ADD COLUMN IF NOT EXISTS " + quoteIdent(
                            geomColumn) + " geometry";
            st.execute(alter);
        }
    }

    private static void insertFeatures(Connection conn, String tableName, List<ColumnDef> columns,
                                       String geomColumn,
                                       SimpleFeatureCollection collection, int srid, String charset) throws SQLException {
        // Build insert SQL: (col1, col2, geom) values (?, ?, ST_GeomFromText(?, srid))
        StringBuilder sbCols = new StringBuilder();
        StringBuilder sbVals = new StringBuilder();
        for (ColumnDef col : columns) {
            if (sbCols.length() > 0) {
                sbCols.append(", ");
                sbVals.append(", ");
            }
            sbCols.append(quoteIdent(col.name));
            sbVals.append("?");
        }
        if (sbCols.length() > 0) {
            sbCols.append(", ");
            sbVals.append(", ");
        }
        sbCols.append(quoteIdent(geomColumn));
        sbVals.append("ST_GeomFromText(?, " + srid + ")");

        String insertSql = "INSERT INTO " + quoteIdent(tableName) +
                " (" + sbCols.toString() + ") VALUES (" + sbVals.toString() + ")";
        System.out.println("Insert SQL: " + insertSql);

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            int processed = 0;
            long executedCount = 0;
            boolean hasUnknownBatchCounts = false;
            WKTWriter wktWriter = new WKTWriter();

            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    int paramIndex = 1;
                    for (ColumnDef col : columns) {
                        Object val = f.getAttribute(col.name);
                        // Convert date types to timestamp for PreparedStatement
                        if (val instanceof Date) {
                            ps.setTimestamp(paramIndex++, new Timestamp(((Date) val).getTime()));
                        } else if (val instanceof java.sql.Timestamp) {
                            ps.setTimestamp(paramIndex++, (java.sql.Timestamp) val);
                        } else if (val instanceof Number) {
                            if (val instanceof Integer) {
                                ps.setInt(paramIndex++, ((Number) val).intValue());
                            } else if (val instanceof Long) {
                                ps.setLong(paramIndex++, ((Number) val).longValue());
                            } else {
                                ps.setDouble(paramIndex++, ((Number) val).doubleValue());
                            }
                        } else if (val instanceof Boolean) {
                            ps.setBoolean(paramIndex++, (Boolean) val);
                        } else if (val == null) {
                            ps.setObject(paramIndex++, null);
                        } else {
                            ps.setString(paramIndex++, val.toString());
                        }
                    }
                    // geometry
                    Object geomObj = f.getAttribute(geomColumn);
                    String wkt = null;
                    if (geomObj != null) {
                        // Try to get WKT using JTS
                        try {
                            wkt = wktWriter.write((org.locationtech.jts.geom.Geometry) geomObj);
                            // 确保WKT字符串正确编码
                            byte[] wktBytes = wkt.getBytes(StandardCharsets.UTF_8);
                            wkt = new String(wktBytes, StandardCharsets.UTF_8);
                        } catch (ClassCastException e) {
                            // Some shapefiles may give different geometry types; fallback to toString
                            wkt = geomObj.toString();
                            byte[] wktBytes = wkt.getBytes(StandardCharsets.UTF_8);
                            wkt = new String(wktBytes, StandardCharsets.UTF_8);
                        }
                    }
                    // geometry param is the last parameter
                    ps.setString(columns.size() + 1, wkt);

                    ps.addBatch();
                    processed++;

                    if (processed % BATCH_SIZE == 0) {
                        int[] results = ps.executeBatch();
                        conn.commit();
                        // accumulate results
                        for (int r : results) {
                            if (r >= 0) {
                                executedCount += r;
                            } else if (r == Statement.SUCCESS_NO_INFO) {
                                hasUnknownBatchCounts = true;
                            }
                            // EXECUTE_FAILED (-3) would be thrown as SQLException usually
                        }
                        System.out.println("Inserted " + processed + " features...");
                    }
                }
            }

            // execute any remaining batch
            int[] finalResults = ps.executeBatch();
            conn.commit();
            for (int r : finalResults) {
                if (r >= 0) {
                    executedCount += r;
                } else if (r == Statement.SUCCESS_NO_INFO) {
                    hasUnknownBatchCounts = true;
                }
            }

            // Print a helpful summary
            if (!hasUnknownBatchCounts) {
                System.out.println("Inserted total " + executedCount + " features - batch executed");
            } else {
                System.out.println("Inserted approximately " + executedCount
                        + " features (some batch counts unknown), processed " + processed
                        + " features in total.");
            }

            // Optional: verify exact DB count (can be slower for large tables). Comment out if unneeded.
            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT COUNT(*) FROM " + quoteIdent(tableName))) {
                if (rs.next()) {
                    long dbCount = rs.getLong(1);
                    System.out.println("DB reports " + dbCount + " rows in table " + tableName);
                }
            } catch (SQLException e) {
                // don't fail the import if the verification query fails; just warn
                System.err.println("Warning: failed to verify row count: " + e.getMessage());
            }
        }
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
    }

    private static class ColumnDef {

        final String name;
        final String sqlType;

        ColumnDef(String name, String sqlType) {
            this.name = name;
            this.sqlType = sqlType;
        }
    }
}
