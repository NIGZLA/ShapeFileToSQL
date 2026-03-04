package com.nigzla.mapping;

/**
 * @author NIGZLA
 * 2026/3/4 10:26
 */
public class SqlTypeMapper {
    public String map(Class<?> c) {
        if (String.class.isAssignableFrom(c)) {
            return "text";
        }
        if (Integer.class.isAssignableFrom(c)) {
            return "integer";
        }
        if (Long.class.isAssignableFrom(c)) {
            return "bigint";
        }
        if (Double.class.isAssignableFrom(c)) {
            return "double precision";
        }
        if (Boolean.class.isAssignableFrom(c)) {
            return "boolean";
        }
        return "text";
    }
}
