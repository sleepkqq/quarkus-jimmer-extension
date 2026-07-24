package io.quarkiverse.jimmer.runtime.repo.support

import io.quarkiverse.jimmer.runtime.repo.UuidV7Page
import io.quarkiverse.jimmer.runtime.repo.UuidV7Slice
import java.util.UUID
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.kt.ast.expression.`gt?`
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression

import io.quarkiverse.jimmer.runtime.repo.KotlinRepository
import io.quarkiverse.jimmer.runtime.repo.PageParam
import io.quarkiverse.jimmer.runtime.repository.orderBy
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.Slice
import org.babyfish.jimmer.View
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode
import org.babyfish.jimmer.sql.fetcher.DtoMetadata
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.KExecutable
import org.babyfish.jimmer.sql.kt.ast.mutation.*
import org.babyfish.jimmer.sql.kt.ast.query.KConfigurableBaseQuery
import org.babyfish.jimmer.sql.kt.ast.query.KConfigurableRootQuery
import org.babyfish.jimmer.sql.kt.ast.query.KMutableBaseQuery
import org.babyfish.jimmer.sql.kt.ast.query.KMutableRecursiveBaseQuery
import org.babyfish.jimmer.sql.kt.ast.query.KMutableRootQuery
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import org.babyfish.jimmer.sql.kt.ast.table.KBaseTableSymbol
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullBaseTable
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable
import org.babyfish.jimmer.sql.kt.ast.table.KPropsWeakJoinFun
import org.babyfish.jimmer.sql.kt.ast.table.KRecursiveRef
import kotlin.reflect.KClass

/**
 * The base implementation of [KotlinRepository]
 *
 * If the repository
 */
abstract class AbstractKotlinRepository<E: Any, ID: Any>(protected val sql: KSqlClient) : KotlinRepository<E, ID> {

    @Suppress("UNCHECKED_CAST")
    protected val entityType: KClass<E> = RepoOperationsData.getEntityClass(this.javaClass).let { it.kotlin as KClass<E> }

    protected val immutableType: ImmutableType = ImmutableType.get(this.entityType.java)

    override fun findById(id: ID, fetcher: Fetcher<E>?): E? =
        if (fetcher == null) {
            sql.findById(entityType, id)
        } else {
            sql.findById(fetcher, id)
        }

    override fun <V : View<E>> findById(id: ID, viewType: KClass<V>): V? =
        sql.findById(viewType, id)

    override fun findByIds(ids: Iterable<ID>, fetcher: Fetcher<E>?): List<E> =
        if (fetcher == null) {
            sql.findByIds(entityType, ids)
        } else {
            sql.findByIds(fetcher, ids)
        }

    override fun <V : View<E>> findByIds(ids: Iterable<ID>, viewType: KClass<V>): List<V> =
        sql.findByIds(viewType, ids)

    override fun findMapByIds(ids: Iterable<ID>, fetcher: Fetcher<E>?): Map<ID, E> =
        if (fetcher == null) {
            sql.findMapByIds(entityType, ids)
        } else {
            sql.findMapByIds(fetcher, ids)
        }

    @Suppress("UNCHECKED_CAST")
    override fun <V : View<E>> findMapByIds(ids: Iterable<ID>, viewType: KClass<V>): Map<ID, V> =
        DtoMetadata.of(viewType.java).let { metadata ->
            val idPropId = immutableType.idProp.id
            sql.findByIds(metadata.fetcher, ids).associateBy({
                (it as ImmutableSpi).__get(idPropId) as ID
            }) {
                metadata.converter.apply(it)
            }
        }

    override fun findAll(fetcher: Fetcher<E>?, block: (SortDsl<E>.() -> Unit)?): List<E> =
        if (fetcher == null) {
            sql.entities.findAll(entityType, block)
        } else {
            sql.entities.findAll(fetcher, block)
        }

    override fun <V : View<E>> findAll(viewType: KClass<V>, block: (SortDsl<E>.() -> Unit)?): List<V> =
        sql.entities.findAllViews(viewType, block)

    override fun findPage(
        pageParam: PageParam,
        fetcher: Fetcher<E>?,
        block: (SortDsl<E>.() -> Unit)?
    ): Page<E> =
        sql.createQuery(entityType) {
            orderBy(block)
            select(table.fetch(fetcher))
        }.fetchPage(pageParam.index, pageParam.size)

    override fun <V : View<E>> findPage(
        pageParam: PageParam,
        viewType: KClass<V>,
        block: (SortDsl<E>.() -> Unit)?
    ): Page<V> =
        sql.createQuery(entityType) {
            orderBy(block)
            select(table.fetch(viewType))
        }.fetchPage(pageParam.index, pageParam.size)

    override fun findSlice(
        limit: Int,
        offset: Int,
        fetcher: Fetcher<E>?,
        block: (SortDsl<E>.() -> Unit)?
    ): Slice<E> =
        sql.createQuery(entityType) {
            orderBy(block)
            select(table.fetch(fetcher))
        }.fetchSlice(limit, offset)

    override fun <V : View<E>> findSlice(
        limit: Int,
        offset: Int,
        viewType: KClass<V>,
        block: (SortDsl<E>.() -> Unit)?
    ): Slice<V> =
        sql.createQuery(entityType) {
            orderBy(block)
            select(table.fetch(viewType))
        }.fetchSlice(limit, offset)

    override fun findUuidV7Slice(
        limit: Int,
        after: UUID?,
        fetcher: Fetcher<E>?
    ): UuidV7Slice<E> =
        uuidV7Slice(limit, after) { fetch(fetcher) }

    override fun <V : View<E>> findUuidV7Slice(
        limit: Int,
        after: UUID?,
        viewType: KClass<V>
    ): UuidV7Slice<V> =
        uuidV7Slice(limit, after) { fetch(viewType) }

    override fun findUuidV7Page(
        limit: Int,
        after: UUID?,
        fetcher: Fetcher<E>?
    ): UuidV7Page<E> =
        uuidV7Page(limit, after) { fetch(fetcher) }

    override fun <V : View<E>> findUuidV7Page(
        limit: Int,
        after: UUID?,
        viewType: KClass<V>
    ): UuidV7Page<V> =
        uuidV7Page(limit, after) { fetch(viewType) }

    override fun saveCommand(
        entity: E,
        block: (KSaveCommandDsl.() -> Unit)?
    ): KSimpleEntitySaveCommand<E> =
        sql.saveCommand(entity, block)

    override fun saveEntitiesCommand(
        entities: Iterable<E>,
        block: (KSaveCommandDsl.() -> Unit)?
    ): KBatchEntitySaveCommand<E> =
        sql.saveEntitiesCommand(entities, block)

    override fun deleteById(id: ID, deleteMode: DeleteMode): Int =
        sql.deleteById(entityType, id, deleteMode).affectedRowCount(entityType)

    override fun deleteByIds(ids: Iterable<ID>, deleteMode: DeleteMode): Int =
        sql.deleteByIds(entityType, ids, deleteMode).affectedRowCount(entityType)

    protected fun <R> executeQuery(
        block: KMutableRootQuery.ForEntity<E>.() -> KConfigurableRootQuery<KNonNullTable<E>, R>
    ): List<R> =
        sql.createQuery(entityType, block).execute()

    protected fun <R> createQuery(
        block: KMutableRootQuery.ForEntity<E>.() -> KConfigurableRootQuery<KNonNullTable<E>, R>
    ): KConfigurableRootQuery<KNonNullTable<E>, R> =
        sql.createQuery(entityType, block)

    protected fun createUpdate(
        block: KMutableUpdate<E>.() -> Unit
    ): KExecutable<Int> =
        sql.createUpdate(entityType, block)

    protected fun createDelete(
        block: KMutableDelete<E>.() -> Unit
    ): KExecutable<Int> =
        sql.createDelete(entityType, block)

    protected fun <E : Any, B : KNonNullBaseTable<*>> createBaseQuery(
        entityType: KClass<E>,
        block: KMutableBaseQuery<E>.() -> KConfigurableBaseQuery<B>
    ): KConfigurableBaseQuery<B> =
        sql.createBaseQuery(entityType, block)

    protected fun <E : Any, B : KNonNullBaseTable<*>> createBaseQuery(
        entityType: KClass<E>,
        recursiveRef: KRecursiveRef<B>,
        joinBlock: KPropsWeakJoinFun<KNonNullTable<E>, B>,
        block: KMutableRecursiveBaseQuery<E, B>.() -> KConfigurableBaseQuery<B>
    ): KConfigurableBaseQuery<B> =
        sql.createBaseQuery(
            entityType,
            recursiveRef,
            joinBlock,
            block
        )

    protected fun <B: KNonNullBaseTable<*>, R> createQuery(
        symbol: KBaseTableSymbol<B>,
        block: KMutableRootQuery<B>.() -> KConfigurableRootQuery<B, R>
    ): KConfigurableRootQuery<B, R> =
        sql.createQuery(symbol, block)

    private fun <R> uuidV7Slice(
        limit: Int,
        after: UUID?,
        selection: KNonNullTable<E>.() -> Selection<R>
    ): UuidV7Slice<R> {
        require(limit > 0) { "limit must be greater than 0" }
        require(limit < Int.MAX_VALUE) { "limit must be less than Int.MAX_VALUE" }
        require(org.babyfish.jimmer.meta.ImmutableType.get(entityType.java).idProp.returnClass == UUID::class.java) {
            "UUIDv7 paging requires a UUID entity id"
        }
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
        val totalRowCount = sql.createQuery(entityType) {
            select(org.babyfish.jimmer.sql.kt.ast.expression.count(table))
        }.fetchOne()
        return UuidV7Page(slice.rows, slice.nextCursor, totalRowCount)
    }
}
