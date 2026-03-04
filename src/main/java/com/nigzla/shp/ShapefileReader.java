package com.nigzla.shp;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeatureType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author NIGZLA
 * 2026/3/4 10:02
 */
public class ShapefileReader {
    public ShapefileContext read(Path shp, String charset) throws Exception {

        Map<String, Object> map = new HashMap<>();
        map.put("url", shp.toUri().toURL());
        map.put("charset", charset);

        DataStore ds = DataStoreFinder.getDataStore(map);
        String typeName = ds.getTypeNames()[0];

        SimpleFeatureCollection collection =
                (SimpleFeatureCollection) ds.getFeatureSource(typeName).getFeatures();

        SimpleFeatureType schema = collection.getSchema();

        return new ShapefileContext(collection, schema,
                schema.getCoordinateReferenceSystem());
    }
}
