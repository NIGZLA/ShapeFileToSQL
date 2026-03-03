# shp2pg

<div align="center">
	<img src="src/main/resources/02.png" width="160" />
	<h1>SoybeanAdmin ElementPlus</h1>
  <span><a href="./README.md">中文</a> | English</span>
</div>

Java utility to unzip a zipped Shapefile, convert features to SQL and insert into a Postgres/PostGIS table.

Requirements:

- Java 11+
- Maven
- PostgreSQL with PostGIS enabled (CREATE EXTENSION postgis;)

How to build:

- mvn package
- The resulting fat jar will be in target/shp2pg-0.1.0.jar

Usage:

```bash
java -jar shp2pg-0.1.0.jar <zip-path> <table-name> <jdbc-url> <db-user> <db-password> [srid]
```

Parameters:

- zip-path: path to the zipped shapefile (.zip) containing at least .shp, .shx, .dbf (and optional .prj)
- table-name: destination table name in Postgres (will be created if not exists)
- jdbc-url: jdbc:postgresql://host:port/database
- db-user, db-password: credentials
- srid (optional): force SRID (integer). If omitted, tries to read from .prj; if fails, defaults to 4326.

Notes:

- The program will map shapefile attribute types to common Postgres types (text, integer, double precision, boolean,
  timestamp).
- Geometry is inserted using ST_GeomFromText(WKT, SRID).
- For large datasets, consider tuning batch size or using shp2pgsql (PostGIS tool) which can be faster. This utility is
  for integration into Java workflows.

Example:

```bash
java -jar target/shp2pg-0.1.0.jar data/cities.zip cities jdbc:postgresql://localhost:5432/mydb myuser mypass 4326
```

