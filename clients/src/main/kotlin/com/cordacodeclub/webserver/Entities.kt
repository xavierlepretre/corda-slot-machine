package com.cordacodeclub.webserver

import com.cordacodeclub.flows.GameResult
import com.cordacodeclub.flows.LeaderboardFlows

// don't change the name or types of the elements of this class
// they are as expected by the client-side JavaScript

data class Prize(val id: Int, val payout_credits: Long, val payout_winnings: Long) {
    constructor(payout_credits: Long) : this(0, payout_credits, 0L)
}

data class SpinResult(
        val success: Boolean,
        val error: String?,
        val prize: Prize?,
        val balance: Long,
        val day_winnings: Long,
        val lifetime_winnings: Long,
        val last_win: Long) {

    constructor(result: GameResult) : this(
            success = result.error == null,
            error = result.error,
            prize = if (result.payout_credits != 0L) Prize(result.payout_credits) else null,
            balance = result.balance,
            day_winnings = 0,
            lifetime_winnings = 0,
            last_win = 0)

    constructor(error: String) : this(
            success = false,
            error = error,
            prize = null,
            balance = 0,
            day_winnings = 0,
            lifetime_winnings = 0,
            last_win = 0)
}

data class LeaderboardEntryResult(
        val success: Boolean,
        val error: String?) {
    constructor() : this(true, null)
    constructor(error: String) : this(false, error)
}

// Do not add the player's account name as at the moment we count on it to be like a password.
data class LeaderboardEntry(
        val nickname: String,
        val total: Long,
        val creationDate: String,
        val linearId: String) {
    constructor(entry: LeaderboardFlows.LeaderboardNamedEntryState)
            : this(entry.nickname,
            entry.state.state.data.total.quantity,
            entry.state.state.data.creationDate.toString(),
            entry.state.state.data.linearId.id.toString())
}

data class Leaderboard(val entries: List<LeaderboardEntry>) {
    companion object {
        fun fromNamedEntries(entries: List<LeaderboardFlows.LeaderboardNamedEntryState>) = entries
                .map(::LeaderboardEntry)
    }
}