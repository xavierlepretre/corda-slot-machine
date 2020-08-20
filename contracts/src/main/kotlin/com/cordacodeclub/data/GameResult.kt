package com.cordacodeclub.data

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class GameResult private constructor(
        val error: String?,
        val reels: IntArray,
        val payout_credits: Int,
        val balance: Int? = null) {

    companion object {
        const val reelCount = 3
        const val reelPositionCount = 6
    }

    init {
        if (error != null) {
            // nothing else matters
        } else {
            require(reels.size == reelCount) { "There must be $reelCount reels" }
            require(reels.all { it in 1..reelPositionCount }) {
                "Reel position must be in the range 1 through $reelPositionCount"
            }
            require(0 <= payout_credits) { "Payout credits must not be negative" }
        }
    }

    public constructor(
            reels: IntArray,
            payout_credits: Int,
            balance: Int? = null) : this(null, reels, payout_credits, balance)

    constructor(error: String) : this(error, intArrayOf(0, 0, 0), 0) {}
}
