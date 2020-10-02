package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class QuickConfig(
        val notaryName: String,
        val notary: Party,
        val casinoHostName: String,
        val casinoHost: Party,
        val playerHostName: String,
        val playerHost: Party)

@StartableByRPC
class GetNotaryAndCasino() : FlowLogic<QuickConfig>() {

    companion object {
        const val configNotaryKey = "notary"
        const val configCasinoHostKey = "casinoHost"
        const val configPlayerHostKey = "playerHost"

        fun ServiceHub.getPlayerHost() = getAppContext().config
                .getString(configPlayerHostKey)
                .let { CordaX500Name.parse(it) }
                .let { identityService.wellKnownPartyFromX500Name(it) }
                ?: throw FlowException("Player host not found")
    }

    @Suspendable
    override fun call(): QuickConfig {
        val config = serviceHub.getAppContext().config
        val notaryName = config.getString(configNotaryKey)
        val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse(notaryName))
                ?: throw FlowException("Notary $notaryName not found")
        val casinoHostName = config.getString(configCasinoHostKey)
        val casinoHost = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(casinoHostName))
                ?: throw FlowException("Casino $casinoHostName not found")
        val playerHostName = config.getString(configPlayerHostKey)
        val playerHost = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(playerHostName))
                ?: throw FlowException("Player $playerHostName not found")
        return QuickConfig(notaryName, notary, casinoHostName, casinoHost, playerHostName, playerHost)
    }
}