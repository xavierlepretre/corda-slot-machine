package com.cordacodeclub.schema

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for LockableTokenState.
 */
object LockableTokenSchema

/**
 * An LockableTokenState schema.
 */
object LockableTokenSchemaV1 : MappedSchema(
        schemaFamily = LockableTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentLockableToken::class.java)) {
    @Entity
    @Table(name = "lockable_token_states")
    class PersistentLockableToken(
            @Column(name = "holder")
            var holder: ByteArray?,
            @Column(name = "issuer", nullable = false)
            var issuer: ByteArray,
            @Column(name = "amount", nullable = false)
            var amount: Long) : PersistentState() {
        // Default constructor required by hibernate.
        constructor() : this(null, ByteArray(0), 0)
    }
}