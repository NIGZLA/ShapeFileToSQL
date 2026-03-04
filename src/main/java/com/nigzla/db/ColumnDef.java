package com.nigzla.db;

import lombok.Data;

/**
 * @author NIGZLA
 * 2026/3/4 10:42
 */
@Data
public class ColumnDef {
    public String name;
    public String sqlType;


    public ColumnDef(String name, String sqlType) {
        this.name = name;
        this.sqlType = sqlType;
    }
}
