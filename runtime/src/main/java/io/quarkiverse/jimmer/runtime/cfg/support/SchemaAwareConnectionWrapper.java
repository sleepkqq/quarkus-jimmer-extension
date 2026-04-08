package io.quarkiverse.jimmer.runtime.cfg.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.regex.Pattern;

/**
 * Wraps a JDBC {@link Connection} to prefix Jimmer's internal table
 * {@code jimmer_trans_cache_operator} with the configured default schema.
 * <p>
 * This is a workaround for PostgreSQL environments where
 * {@code Connection.setSchema()} does not reliably affect the search path
 * for all statements (e.g., pooled connections, certain JDBC drivers).
 * Jimmer's {@code TransactionCacheOperator} generates SQL referencing
 * this table without a schema qualifier, causing intermittent
 * "relation does not exist" errors.
 */
class SchemaAwareConnectionWrapper implements InvocationHandler {

	private static final Pattern TABLE_PATTERN = Pattern.compile(
			"jimmer_trans_cache_operator", Pattern.CASE_INSENSITIVE);

	private final Connection delegate;
	private final String schema;

	private SchemaAwareConnectionWrapper(Connection delegate, String schema) {
		this.delegate = delegate;
		this.schema = schema;
	}

	static Connection wrap(Connection connection, String schema) {
		if (schema == null || schema.isEmpty()) {
			return connection;
		}
		return (Connection) Proxy.newProxyInstance(
				connection.getClass().getClassLoader(),
				new Class<?>[] { Connection.class },
				new SchemaAwareConnectionWrapper(connection, schema));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (args != null && args.length > 0 && args[0] instanceof String sql) {
			args[0] = TABLE_PATTERN.matcher(sql).replaceAll(schema + ".$0");
		}
		return method.invoke(delegate, args);
	}
}
