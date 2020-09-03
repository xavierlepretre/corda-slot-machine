package com.cordacodeclub.states

import com.cordacodeclub.contracts.LockableTokenContract
import com.cordacodeclub.schema.LockableTokenSchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
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
        val amount: Amount<LockableTokenType>,
        override val participants: List<AbstractParty>) : ContractState, QueryableState {

    init {
        require(isLocked == (holder == null)) { "Is locked should reflect that holder is null" }
    }

    constructor(issuer: AbstractParty, amount: Amount<LockableTokenType>, participants: List<AbstractParty>)
            : this(null, true, issuer, amount, participants)

    constructor(holder: AbstractParty, issuer: AbstractParty, amount: Amount<LockableTokenType>)
            : this(holder, false, issuer, amount, listOf(holder))

    constructor(bettor: Bettor) : this(bettor.holder, bettor.issuedAmount.issuer, bettor.issuedAmount.amount)

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

        return holder == other.holder
                && isLocked == other.isLocked
                && issuer == other.issuer
                && amount == other.amount
    }

    override fun hashCode(): Int {
        var result = holder?.hashCode() ?: 0
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + amount.hashCode()
        return result
    }
}

@CordaSerializable
data class IssuedAmount(val issuer: AbstractParty,
                        val amount: Amount<LockableTokenType>) {
    constructor(issuer: AbstractParty,
                amount: Long) : this(issuer, Amount(amount, LockableTokenType))
}

infix fun Long.issuedBy(issuer: AbstractParty) = IssuedAmount(issuer, this)
infix fun Amount<LockableTokenType>.issuedBy(issuer: AbstractParty) = IssuedAmount(issuer, this)

@CordaSerializable
data class Bettor(val holder: AbstractParty,
                  val issuedAmount: IssuedAmount) {
    constructor(holder: AbstractParty,
                issuer: AbstractParty,
                amount: Long) : this(holder, amount issuedBy issuer)
}

infix fun IssuedAmount.heldBy(holder: AbstractParty) = Bettor(holder, this)

@CordaSerializable
data class Committer(val holder: AbstractParty,
                     val linearId: UniqueIdentifier)

infix fun AbstractParty.commitsTo(linearId: UniqueIdentifier) = Committer(this, linearId)

@CordaSerializable
data class CommittedBettor(val committer: Committer,
                           val issuedAmount: IssuedAmount)

infix fun Committer.with(issuedAmount: IssuedAmount) = CommittedBettor(this, issuedAmount)