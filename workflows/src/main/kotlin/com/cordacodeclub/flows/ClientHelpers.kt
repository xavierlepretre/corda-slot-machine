package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class QuickConfig(
        val notaryName: String,
        val notary: Party,
        val casinoHostName: String,
        val casinoHost: Party)

@StartableByRPC
class GetNotaryAndCasino() : FlowLogic<QuickConfig>() {

    companion object {
        const val configNotaryKey = "notary"
        const val configCasinoHostKey = "casinoHost"
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
        return QuickConfig(notaryName, notary, casinoHostName, casinoHost)
    }
}