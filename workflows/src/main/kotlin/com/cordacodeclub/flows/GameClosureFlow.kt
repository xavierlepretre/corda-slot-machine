package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.cordacodeclub.states.GameState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*

object GameClosureFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val gameRef: StateAndRef<GameState>): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val playerSession: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}