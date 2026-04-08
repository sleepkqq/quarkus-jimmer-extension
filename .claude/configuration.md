# Configuration

## Build-time (`JimmerBuildTimeConfig`)

- `quarkus.jimmer.enable` (default: true)
- `quarkus.jimmer.language` (default: "java")
- `quarkus.jimmer.micro-service-name` (optional)
- `quarkus.jimmer.<datasource>.show-sql` (default: false)
- `quarkus.jimmer.<datasource>.pretty-sql` (default: false)
- `quarkus.jimmer.<datasource>.inline-sql-variables` (default: false)
- `quarkus.jimmer.<datasource>.trigger-type` (default: BINLOG_ONLY)

## Runtime (`JimmerRuntimeConfig` / `JimmerDataSourceRuntimeConfig`)

- `quarkus.jimmer.transaction-cache-operator-fixed-delay` (default: "5s")
- `quarkus.jimmer.database-validation.mode` (default: NONE)
- `quarkus.jimmer.<datasource>.default-schema`
- `quarkus.jimmer.<datasource>.dialect`
- `quarkus.jimmer.<datasource>.default-batch-size`
- `quarkus.jimmer.<datasource>.default-list-batch-size`
- `quarkus.jimmer.<datasource>.default-enum-strategy`
- `quarkus.jimmer.<datasource>.default-reference-fetch-type`
- `quarkus.jimmer.<datasource>.mutation-transaction-required`
- `quarkus.jimmer.<datasource>.id-only-target-checking-level`
- `quarkus.jimmer.<datasource>.in-list-padding-enabled`
- `quarkus.jimmer.<datasource>.foreign-key-enabled-by-default`

## Multi-datasource

`<datasource>` — имя datasource (`<default>` для основного). Каждый datasource получает свой `JSqlClient` bean с квалификатором `@DataSource("name")`.
