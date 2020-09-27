package com.cordacodeclub.states

import com.cordacodeclub.contracts.LeaderboardEntryContract
import com.cordacodeclub.schema.LeaderboardEntrySchemaV1
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(LeaderboardEntryContract::class)
data class LeaderboardEntryState(
        val player: AbstractParty,
        val total: Amount<LockableTokenType>,
        val tokenIssuer: AbstractParty,
        val creationDate: Instant,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty> = listOf(player)) : LinearState, QueryableState {

    constructor(player: AbstractParty,
                total: Long,
                tokenIssuer: AbstractParty,
                creationDate: Instant,
                linearId: UniqueIdentifier = UniqueIdentifier(),
                participants: List<AbstractParty> = listOf(player))
            : this(player, Amount(total, LockableTokenType), tokenIssuer, creationDate, linearId, participants)

    init {
        require(participants.contains(player)) { "The player must be a participant" }
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is LeaderboardEntrySchemaV1 -> LeaderboardEntrySchemaV1.PersistentLeaderboardEntry(
                    this.player.owningKey.encoded,
                    this.total.quantity,
                    this.tokenIssuer.owningKey.encoded,
                    this.creationDate
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(LeaderboardEntrySchemaV1)

}