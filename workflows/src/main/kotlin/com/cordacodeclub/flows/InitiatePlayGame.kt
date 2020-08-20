package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.data.GameResult
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

@InitiatingFlow
@StartableByRPC
class InitiatePlayGame(private val accountName: String) : FlowLogic<GameResult>() {

  @Suspendable
  override fun call(): GameResult {
    return GameResult( intArrayOf(1, 3, 5), 42, 141)
  }
}