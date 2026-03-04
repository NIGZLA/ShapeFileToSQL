package com.nigzla.shp;

import com.nigzla.db.ColumnDef;
import com.nigzla.db.TableMetadata;
import com.nigzla.mapping.GeometryTypeMapper;
import com.nigzla.mapping.SqlTypeMapper;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author NIGZLA
 * 2026/3/4 10:54
 */
public class ShpSchemaParser {
    private final SqlTypeMapper sqlTypeMapper = new SqlTypeMapper();
    private final GeometryTypeMapper geometryTypeMapper = new GeometryTypeMapper();

    public TableMetadata parse(SimpleFeatureType schema) {

        List<ColumnDef> columns = new ArrayList<>();
        String geomColumn = null;
        String geomType = "Geometry";

        for (AttributeDescriptor ad : schema.getAttributeDescriptors()) {

            String name = ad.getLocalName();
            Class<?> binding = ad.getType().getBinding();

            if (ad instanceof GeometryDescriptor) {
                geomColumn = name;
                geomType = geometryTypeMapper.map(binding);
                continue;
            }

            String sqlType = sqlTypeMapper.map(binding);
            columns.add(new ColumnDef(name, sqlType));
        }

        if (geomColumn == null) {
            throw new IllegalStateException("No geometry column found");
        }

        return new TableMetadata(columns, geomColumn, geomType);
    }
}
