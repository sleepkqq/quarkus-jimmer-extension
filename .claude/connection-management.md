# Connection Management

## QuarkusConnectionManager

`runtime/src/main/java/.../cfg/support/QuarkusConnectionManager.java`

Реализует `TxConnectionManager`. Управляет JDBC-соединениями:
- Получает `Connection` из `DataSource` (Agroal pool)
- Применяет `defaultSchema` через `connection.setSchema()`
- Оборачивает все соединения в `SchemaAwareConnectionWrapper` для надёжной квалификации Jimmer-таблиц схемой
- Делегирует транзакции в `QuarkusTransaction`

## SchemaAwareConnectionWrapper

`runtime/src/main/java/.../cfg/support/SchemaAwareConnectionWrapper.java`

Workaround для PostgreSQL: `connection.setSchema()` не всегда надёжно работает с пулом соединений. Jimmer's `TransactionCacheOperator` генерирует SQL с таблицей `jimmer_trans_cache_operator` без префикса схемы → периодические ошибки "relation does not exist".

Решение: `java.lang.reflect.Proxy`-обёртка над `Connection`, которая перехватывает все вызовы с SQL-аргументом и заменяет `jimmer_trans_cache_operator` на `{schema}.jimmer_trans_cache_operator` (case-insensitive).

Применяется в обоих путях `QuarkusConnectionManager.execute()`:
- Новое соединение из пула
- Существующее соединение переданное извне (в контексте транзакции)
