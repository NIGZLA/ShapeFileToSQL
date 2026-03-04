package com.nigzla.app;

import com.nigzla.cli.ConsoleColor;
import com.nigzla.config.ImportConfig;
import com.nigzla.db.FeatureBatchWriter;
import com.nigzla.db.PostgisConnectionFactory;
import com.nigzla.db.TableBuilder;
import com.nigzla.db.TableMetadata;
import com.nigzla.io.ZipExtractor;
import com.nigzla.shp.ShapefileContext;
import com.nigzla.shp.ShapefileLocator;
import com.nigzla.shp.ShapefileReader;
import com.nigzla.shp.ShpSchemaParser;

import java.io.FileWriter;
import java.nio.file.*;
import java.sql.*;
import java.util.Scanner;

/**
 * 1)将 Shapefile 压缩包解压到临时目录
 * 2)使用 GeoTools 读取 Shapefile
 * 3)在 Postgres（PostGIS）中创建数据表，并将要素（features）插入
 * @用法 java -jar shp2pg.jar [压缩包路径] [表名] [数据库主机] [数据库端口] [数据库名] [数据库用户名] [数据库密码] [SRID] [字符集]
 * @Usage: java -jar shp2pg.jar [zip-path] [table-name] [host] [port] [database] [db-user] [db-pass] [srid] [charset]
 * @author NIGZLA
 */
public class Shp2PG {

    public static void main(String[] args) throws Exception {

        System.out.println(ConsoleColor.title("Shp2PG CLI"));
        System.out.println(ConsoleColor.info("Type 'shp2pg --help' for usage."));
        System.out.println(ConsoleColor.info("Type 'exit' to quit."));
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(ConsoleColor.info("shp2pg> "));
            String line = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(line)) {
                break;
            }

            if (line.isEmpty()) {
                continue;
            }

            String[] inputArgs = line.split("\\s+");

            if ("--help".equalsIgnoreCase(inputArgs[0]) ||
                    ("shp2pg".equalsIgnoreCase(inputArgs[0]) &&
                            inputArgs.length > 1 &&
                            "--help".equalsIgnoreCase(inputArgs[1]))) {

                printHelp();
                continue;
            }

            if (!"shp2pg".equalsIgnoreCase(inputArgs[0])) {
                System.out.println(ConsoleColor.error("Invalid command. Must start with 'shp2pg'"));
                continue;
            }

            if (inputArgs.length < 3) {
                printHelp();
                continue;
            }

            try {
                execute(inputArgs);
            } catch (Exception e) {
                System.out.println(ConsoleColor.error("Error: " + e.getMessage()));
            }
        }

        scanner.close();
        System.out.println(ConsoleColor.success("Bye."));
    }

    private static void execute(String[] args) throws Exception {

        ImportConfig config = ImportConfig.fromCli(args);

        ZipExtractor extractor = new ZipExtractor();
        ShapefileLocator locator = new ShapefileLocator();
        ShapefileReader reader = new ShapefileReader();
        ShpSchemaParser parser = new ShpSchemaParser();

        Path workDir = extractor.extract(config.getZipPath());
        Path shp = locator.findShp(workDir);

        ShapefileContext context = reader.read(shp, config.getCharset());
        TableMetadata metadata = parser.parse(context.getSchema());

        TableBuilder tableBuilder = new TableBuilder();
        FeatureBatchWriter writer = new FeatureBatchWriter();

        if (config.isSqlOnly()) {

            try (FileWriter fw = new FileWriter(config.getOutputSqlFile())) {

                fw.write(tableBuilder.buildCreateTableSql(
                        config.getTableName(),
                        metadata,
                        config.getSrid()));

                fw.write("\n");

                fw.write(writer.buildInsertSql(
                        config.getTableName(),
                        metadata,
                        context,
                        config.getSrid()));
            }

            System.out.println(ConsoleColor.success("SQL file generated: " + config.getOutputSqlFile()));
            return;
        }

        PostgisConnectionFactory factory = new PostgisConnectionFactory();

        try (Connection conn = factory.create(config)) {
            conn.setAutoCommit(false);

            tableBuilder.create(conn, config.getTableName(), metadata, config.getSrid());
            writer.write(conn, config.getTableName(), metadata, context, config.getSrid());

            conn.commit();
        }

        System.out.println(ConsoleColor.success("Import finished successfully."));
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  shp2pg <zip> <table> [host port database user pass] [options]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  Import mode (requires DB params)");
        System.out.println("  SQL mode (-t, no DB required)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --s 4326           Set SRID (default 4326)");
        System.out.println("  --c UTF-8          Set charset (default UTF-8)");
        System.out.println("  -t                 Generate SQL only");
        System.out.println("  -o output.sql      Output SQL file (with -t)");
        System.out.println();
    }
}
