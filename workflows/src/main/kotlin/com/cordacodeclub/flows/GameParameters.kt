package com.cordacodeclub.flows

import net.corda.core.serialization.CordaSerializable
import java.time.Duration

object GameParameters {

    val commitDuration = Duration.ofMinutes(2)!!
    val revealDuration = Duration.ofMinutes(2)!!

}

@CordaSerializable
class GameResult private constructor(
        val error: String?,
        val payout_credits: Long,
        val balance: Long) {

    init {
        if (error != null) {
            // nothing else matters
        } else {
            require(0 <= payout_credits) { "Payout credits must not be negative" }
            // caution -- in addition the payout must be one of several values expected by the front end
        }
    }

    constructor(
            payout_credits: Long,
            balance: Long) : this(null, payout_credits, balance)

    constructor(error: String, balance: Long) : this(error, 0L, balance)
}