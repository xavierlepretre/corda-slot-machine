package com.cordacodeclub.webserver

import com.cordacodeclub.data.GameResult

// don't change the name or types of the elements of this class
// they are as expected by the client-side JavaScript

data class Prize(val id: Int, val payout_credits: Int, val payout_winnings: Int) {
  constructor(payout_credits: Int) : this(0, payout_credits, 0)
}

data class SpinResult(
  val success: Boolean,
  val error: String?,
  val reels: IntArray,
  val prize: Prize?,
  val balance: Int
) {

  constructor(result: GameResult) : this(
    result.error == null,
    result.error,
    result.reels,
    if (result.payout_credits != 0) Prize(result.payout_credits) else null,
    result.balance!!
  )

  constructor(error: String) : this(
    false, error, intArrayOf(0, 0, 0), null, 0
  )
}