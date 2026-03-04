package com.nigzla.db;

import com.nigzla.config.ImportConfig;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * @author NIGZLA
 * 2026/3/4 10:55
 */
public class PostgisConnectionFactory {
    public Connection create(ImportConfig config) throws Exception {

        return DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUser(),
                config.getPassword()
        );
    }
}
