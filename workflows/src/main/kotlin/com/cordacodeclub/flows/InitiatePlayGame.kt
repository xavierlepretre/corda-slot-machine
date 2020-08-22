package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class GameResult private constructor(
  val error: String?,
  val payout_credits: Int,
  val balance: Int) {

  init {
    if (error != null) {
      // nothing else matters
    } else {
      require(0 <= payout_credits) { "Payout credits must not be negative" }
      // caution -- in addition the payout must be one of several values expected by the front end
    }
  }

  public constructor(
    payout_credits: Int,
    balance: Int) : this(null, payout_credits, balance)

  constructor(error: String, balance: Int) : this(error, 0, 0) {}
}

@InitiatingFlow
@StartableByRPC
class InitiatePlayGame(private val accountName: String) : FlowLogic<GameResult>() {

  @Suspendable
  override fun call(): GameResult {
    return GameResult(15, 141)
  }
}