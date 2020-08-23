package com.cordacodeclub.states

import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.schema.LockableTokenSchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
object LockableTokenType

@BelongsToContract(LockableTokenContract::class)
class LockableTokenState private constructor(
        val holder: AbstractParty?,
        val isLocked: Boolean,
        val issuer: AbstractParty,
        val amount: Amount<LockableTokenType>) : ContractState, QueryableState {

    constructor(issuer: AbstractParty, amount: Amount<LockableTokenType>)
            : this(null, true, issuer, amount)

    constructor(holder: AbstractParty, issuer: AbstractParty, amount: Amount<LockableTokenType>)
            : this(holder, false, issuer, amount)

    override val participants = listOfNotNull(holder)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is LockableTokenSchemaV1 -> LockableTokenSchemaV1.PersistentLockableToken(
                    this.holder?.owningKey?.encoded,
                    this.issuer.owningKey.encoded,
                    this.amount.quantity
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(LockableTokenSchemaV1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LockableTokenState

        if (holder != other.holder) return false
        if (isLocked != other.isLocked) return false
        if (issuer != other.issuer) return false
        if (amount != other.amount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = holder?.hashCode() ?: 0
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + amount.hashCode()
        return result
    }
}