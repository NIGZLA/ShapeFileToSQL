package com.nigzla.config;

import lombok.Data;

@Data
public class ImportConfig {

    private String zipPath;
    private String tableName;

    private String host;
    private int port;
    private String database;

    private String user;
    private String password;

    private Integer srid = 4326;
    private String charset = "UTF-8";

    // -t 相关
    private boolean sqlOnly = false;
    private String outputSqlFile = "output.sql";

    public static ImportConfig fromCli(String[] args) {

        if (args.length < 3) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        ImportConfig config = new ImportConfig();

        config.zipPath = args[1];
        config.tableName = args[2];

        // 先扫描是否 -t
        for (String arg : args) {
            if ("-t".equals(arg)) {
                config.sqlOnly = true;
                break;
            }
        }

        int index = 3;

        // 如果不是 sqlOnly，才解析数据库参数
        if (!config.sqlOnly) {

            if (args.length < 8) {
                throw new IllegalArgumentException("Database parameters required.");
            }

            config.host = args[index++];
            config.port = Integer.parseInt(args[index++]);
            config.database = args[index++];
            config.user = args[index++];
            config.password = args[index++];
        }

        // 解析可选参数
        while (index < args.length) {
            switch (args[index]) {

                case "--s":
                    config.srid = Integer.parseInt(args[++index]);
                    break;

                case "--c":
                    config.charset = args[++index];
                    break;

                case "-t":
                    config.sqlOnly = true;
                    break;

                case "-o":
                    config.outputSqlFile = args[++index];
                    break;

                default:
                    throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
            index++;
        }

        return config;
    }

    public String getJdbcUrl() {
        return String.format(
                "jdbc:postgresql://%s:%d/%s",
                host,
                port,
                database
        );
    }
}