package com.cordacodeclub.states

import com.cordacodeclub.contracts.LeaderboardEntryContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.time.Instant

@BelongsToContract(LeaderboardEntryContract::class)
data class LeaderboardEntryState(
        val player: AbstractParty,
        val total: Amount<LockableTokenType>,
        val tokenIssuer: AbstractParty,
        val creationDate: Instant,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty> = listOf(player)) : LinearState {

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

}