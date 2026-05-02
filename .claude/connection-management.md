# Connection Management

## QuarkusConnectionManager

`runtime/src/main/java/.../cfg/support/QuarkusConnectionManager.java`

Реализует `TxConnectionManager`. Управляет JDBC-соединениями:
- Получает `Connection` из `DataSource` (Agroal pool)
- Делегирует транзакции в `QuarkusTransaction`
