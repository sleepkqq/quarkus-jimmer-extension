package io.quarkiverse.jimmer.runtime.repository.support

import io.quarkiverse.jimmer.runtime.repo.UuidV7Page
import io.quarkiverse.jimmer.runtime.repo.UuidV7Slice
import java.util.UUID
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.kt.ast.expression.`gt?`
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable

import io.quarkiverse.jimmer.runtime.repository.KRepository
import io.quarkiverse.jimmer.runtime.repository.common.Sort
import io.quarkiverse.jimmer.runtime.repository.orderBy
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.View
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.mutation.KBatchSaveResult
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandDsl
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import kotlin.reflect.KClass

open class KRepositoryImpl<E: Any, ID: Any> (override val sql: KSqlClient, entityType: Class<E>? = null): KRepository<E, ID> {

    init {
        Utils.validateSqlClient(sql.javaClient)
    }

    @Suppress("UNCHECKED_CAST")
    final override val entityType: KClass<E> = entityType?.kotlin ?: throw IllegalArgumentException("Entity type cannot be null")

    override val type: ImmutableType =
        ImmutableType.get(this.entityType.java)

    override fun findNullable(id: ID, fetcher: Fetcher<E>?): E? =
        if (fetcher !== null) {
            sql.entities.findById(fetcher, id)
        } else {
            sql.entities.findById(entityType, id)
        }

    override fun findByIds(ids: Iterable<ID>, fetcher: Fetcher<E>?): List<E> =
        if (fetcher !== null) {
            sql.entities.findByIds(fetcher, Utils.toCollection(ids))
        } else {
            sql.entities.findByIds(entityType, Utils.toCollection(ids))
        }

    override fun findMapByIds(ids: Iterable<ID>, fetcher: Fetcher<E>?): Map<ID, E> =
        if (fetcher !== null) {
            sql.entities.findMapByIds(fetcher, Utils.toCollection(ids))
        } else {
            sql.entities.findMapByIds(entityType, Utils.toCollection(ids))
        }

    override fun findAll(fetcher: Fetcher<E>?): List<E> =
        if (fetcher !== null) {
            sql.entities.findAll(fetcher)
        } else {
            sql.entities.findAll(entityType)
        }

    override fun findAll(fetcher: Fetcher<E>?, block: (SortDsl<E>.() -> Unit)): List<E> =
        if (fetcher !== null) {
            sql.entities.findAll(fetcher, block)
        } else {
            sql.entities.findAll(entityType, block)
        }

    override fun findAll(fetcher: Fetcher<E>?, sort: Sort): List<E> =
        sql.createQuery(entityType) {
            orderBy(sort)
            select(table.fetch(fetcher))
        }.execute()

    override fun findAll(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<E>?,
        block: (SortDsl<E>.() -> Unit)?
    ): Page<E> =
        sql.createQuery(entityType) {
            orderBy(block)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

    override fun findAll(pageIndex: Int, pageSize: Int, fetcher: Fetcher<E>?, sort: Sort): Page<E> =
        sql.createQuery(entityType) {
            orderBy(sort)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

    override fun findAll(pagination: Pagination): Page<E> =
        findAll(pagination, null)

    override fun findAll(pagination: Pagination, fetcher: Fetcher<E>?): Page<E> =
        sql.createQuery(entityType) {
            select(table.fetch(fetcher))
        }.fetchPage(pagination.index, pagination.size)

    override fun findUuidV7Slice(
        limit: Int,
        after: UUID?,
        fetcher: Fetcher<E>?
    ): UuidV7Slice<E> =
        uuidV7Slice(limit, after) { fetch(fetcher) }

    override fun findUuidV7Page(
        limit: Int,
        after: UUID?,
        fetcher: Fetcher<E>?
    ): UuidV7Page<E> =
        uuidV7Page(limit, after) { fetch(fetcher) }

    override fun count(): Long =
        sql.createQuery(entityType) {
            select(org.babyfish.jimmer.sql.kt.ast.expression.count(table))
        }.fetchOne()

    override fun delete(entity: E, mode: DeleteMode): Int =
        sql.entities.delete(
            entityType,
            ImmutableObjects.get(entity, type.idProp)
        ) {
            setMode(mode)
        }.affectedRowCount(entityType)

    override fun deleteById(id: ID, mode: DeleteMode): Int =
        sql.entities.delete(entityType, id) {
            setMode(mode)
        }.affectedRowCount(entityType)

    override fun deleteByIds(ids: Iterable<ID>, mode: DeleteMode): Int =
        sql.entities.deleteAll(entityType, Utils.toCollection(ids)) {
            setMode(mode)
        }.affectedRowCount(entityType)

    override fun deleteAll(entities: Iterable<E>, mode: DeleteMode): Int =
        sql
            .entities
            .deleteAll(
                entityType,
                entities.map {
                    ImmutableObjects.get(it, type.idProp)
                }
            ) {
                setMode(mode)
            }.affectedRowCount(entityType)

    override fun deleteAll() {
        sql.createDelete(entityType) {
        }.execute()
    }

    override fun <V : View<E>> viewer(viewType: KClass<V>): KRepository.Viewer<E, ID, V> =
        ViewerImpl(viewType)

    private inner class ViewerImpl<V: View<E>>(private val viewType: KClass<V>): KRepository.Viewer<E, ID, V> {

        override fun findNullable(id: ID): V? =
            sql.entities.findById(viewType, id)

        override fun findByIds(ids: Iterable<ID>?): List<V> =
            sql.entities.findByIds(viewType, Utils.toCollection(ids))

        override fun findMapByIds(ids: Iterable<ID>?): Map<ID, V> =
            sql.entities.findMapByIds(viewType, Utils.toCollection(ids))

        override fun findAll(): List<V> =
            sql.entities.findAll(viewType)

        override fun findAll(sort: Sort): List<V> =
            sql.createQuery(entityType) {
                orderBy(sort)
                select(table.fetch(viewType))
            }.execute()

        override fun findAll(block: SortDsl<E>.() -> Unit): List<V> =
            sql.createQuery(entityType) {
                orderBy(block)
                select(table.fetch(viewType))
            }.execute()

        override fun findAll(pagination: Pagination): Page<V> =
            sql.createQuery(entityType) {
                select(table.fetch(viewType))
            }.fetchPage(pagination.index, pagination.size)

        override fun findAll(pageIndex: Int, pageSize: Int): Page<V> =
            sql.createQuery(entityType) {
                select(table.fetch(viewType))
            }.fetchPage(pageIndex, pageSize)

        override fun findAll(pageIndex: Int, pageSize: Int, sort: Sort): Page<V> =
            sql.createQuery(entityType) {
                orderBy(sort)
                select(table.fetch(viewType))
            }.fetchPage(pageIndex, pageSize)

        override fun findAll(pageIndex: Int, pageSize: Int, block: SortDsl<E>.() -> Unit): Page<V> =
            sql.createQuery(entityType) {
                orderBy(block)
                select(table.fetch(viewType))
            }.fetchPage(pageIndex, pageSize)

        override fun findUuidV7Slice(limit: Int, after: UUID?): UuidV7Slice<V> =
            uuidV7Slice(limit, after) { fetch(viewType) }

        override fun findUuidV7Page(limit: Int, after: UUID?): UuidV7Page<V> =
            uuidV7Page(limit, after) { fetch(viewType) }
    }

    private fun <R> uuidV7Slice(
        limit: Int,
        after: UUID?,
        selection: KNonNullTable<E>.() -> Selection<R>
    ): UuidV7Slice<R> {
        require(limit > 0) { "limit must be greater than 0" }
        require(limit < Int.MAX_VALUE) { "limit must be less than Int.MAX_VALUE" }
        require(type.idProp.returnClass == UUID::class.java) { "UUIDv7 paging requires a UUID entity id" }
        require(after == null || after.version() == 7) { "UUIDv7 paging requires a version 7 cursor" }
        val tuples = sql.createQuery(entityType) {
            val id = table.getId<UUID>() as KNonNullExpression<UUID>
            where(id `gt?` after)
            orderBy(id.asc())
            select(id, table.selection())
        }.limit(limit + 1, 0).execute()
        val hasNext = tuples.size > limit
        val rows = tuples.take(limit)
        return UuidV7Slice(
            rows.map { it._2 },
            if (hasNext) rows.last()._1 else null
        )
    }

    private fun <R> uuidV7Page(
        limit: Int,
        after: UUID?,
        selection: KNonNullTable<E>.() -> Selection<R>
    ): UuidV7Page<R> {
        val slice = uuidV7Slice(limit, after, selection)
        return UuidV7Page(slice.rows, slice.nextCursor, count())
    }
}
