package com.nigzla.mapping;

/**
 * @author NIGZLA
 * 2026/3/4 10:26
 */
public class GeometryTypeMapper {
    public String map(Class<?> geomClass) {
        String name = geomClass.getSimpleName();
        if (name.contains("Point")) {
            return "Point";
        }
        if (name.contains("Polygon")) {
            return "Polygon";
        }
        if (name.contains("LineString")) {
            return "LineString";
        }
        return "Geometry";
    }
}
