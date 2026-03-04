package com.nigzla.db;

import lombok.Data;

import java.util.List;

/**
 * @author NIGZLA
 * 2026/3/4 10:06
 */
@Data
public class TableMetadata {
    private final List<ColumnDef> columns;
    private final String geometryColumn;
    private final String geometryType;
}
