package com.nigzla.shp;

import lombok.Data;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * @author NIGZLA
 * 2026/3/4 10:02
 */
@Data
public class ShapefileContext {

    private final SimpleFeatureCollection collection;
    private final SimpleFeatureType schema;
    private final CoordinateReferenceSystem crs;
}