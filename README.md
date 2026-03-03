# shp2pg

<div align="center">
	<img src="src/main/resources/02.png" width="160" />
	<h1>SoybeanAdmin ElementPlus</h1>
  <span>中文 | <a href="./README.en_US.md">English</a></span>
</div>

Java 工具，用于解压缩 Shapefile，将要素转换为 SQL 并插入 Postgres/PostGIS 表

要求:

- Java 11+
- Maven
- 启用 PostGIS 的 PostgreSQL（CREATE EXTENSION postgis;）

如何建造:

- mvn package
- 生成的 jar 文件将位于 target/shp2pg-0.1.0.jar 中

用法:

```bash
java -jar shp2pg-0.1.0.jar <zip-path> <table-name> <jdbc-url> <db-user> <db-password> [srid]
```

参数:

- zip-path：包含至少 .shp、.shx、.dbf（以及可选的 .prj）文件的压缩 shapefile (.zip) 的路径
- 表名：Postgres 中的目标表名（如果不存在则会创建）
- jdbc-url: jdbc:postgresql://host:port/database
- 数据库用户名、数据库密码：凭据
- srid（可选）：强制指定 SRID（整数）。如果省略，则尝试从 .prj 文件中读取；如果读取失败，则默认为 4326。

笔记:

- 该程序会将 shapefile 属性类型映射到常见的 Postgres 类型（文本、整数、双精度、布尔值、时间戳）
- 使用 ST_GeomFromText(WKT, SRID) 插入几何
- 对于大型数据集，可以考虑调整批处理大小或使用 shp2pgsql（PostGIS 工具），后者速度更快。此实用程序用于集成到 Java 工作流中

示例:

```bash
java -jar target/shp2pg-1.0.jar data/cities.zip cities jdbc:postgresql://localhost:5432/mydb myuser mypass 4326
```

