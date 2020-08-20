package com.cordacodeclub.data

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class GameResult(
  val error: String?,
  val reels: IntArray,
  val payout_credits: Int,
  val balance: Int? = null
) {

  init {
    if (error != null) {
      // nothing else matters
    } else {
      if (reels.size != 3)
        throw IllegalArgumentException("There must be 3 reels")
      if (reels.any { (it <= 0) || (it > 6) })
        throw IllegalArgumentException("Reel position must be in the range 1 through 6")
      if (payout_credits < 0)
        throw IllegalArgumentException("Payout credits must not be negative")
    }
  }

  constructor(error: String) : this(error, intArrayOf(0, 0, 0), 0) {}
}
