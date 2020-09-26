package com.cordacodeclub.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * The family of schemas for LockableTokenState.
 */
object LockableTokenSchema

/**
 * A LockableTokenState schema.
 */
object LockableTokenSchemaV1 : MappedSchema(
        schemaFamily = LockableTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentLockableToken::class.java)) {

    const val COL_HOLDER = "holder"
    const val COL_ISSUER = "issuer"
    const val COL_AMOUNT = "amount"

    @Entity
    @Table(name = "lockable_token_states",
            indexes = [
                Index(name = "lockable_token_holder_ids", columnList = COL_HOLDER),
                Index(name = "lockable_token_issuer_idx", columnList = COL_ISSUER)
            ])
    class PersistentLockableToken(
            @Column(name = COL_HOLDER)
            @Type(type = "corda-wrapper-binary")
            var holder: ByteArray?,
            @Column(name = COL_ISSUER, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var issuer: ByteArray,
            @Column(name = COL_AMOUNT, nullable = false)
            var amount: Long) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this(null, ByteArray(0), 0)
    }
}