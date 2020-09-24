package com.cordacodeclub.webserver

import com.cordacodeclub.flows.GameResult

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
