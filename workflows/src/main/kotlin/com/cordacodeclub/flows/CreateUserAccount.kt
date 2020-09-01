package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

@InitiatingFlow
@StartableByRPC
class CreateUserAccount(private val accountName: String) : FlowLogic<Int>() {

    @Suspendable
    override fun call(): Int {

        // not sure whether this explicit check is necessary or whether CreateAccount can throw
        if (accountService.accountInfo(accountName).any() { it.state.data.host == ourIdentity }) {
            throw FlowException("$accountName account already exists")
        }

        // call inbuilt CreateAccount flow to create the AccountInfo object
        subFlow(CreateAccount(accountName))

        // TODO get the balance
        return 50;
    }
}

@InitiatingFlow
@StartableByRPC
class GetUserBalance(private val accountName: String) : FlowLogic<Int>() {

    @Suspendable
    override fun call(): Int {

        // not sure whether this explicit check is necessary or whether CreateAccount can throw
        if (!accountService.accountInfo(accountName).any() { it.state.data.host == ourIdentity }) {
            throw FlowException("$accountName account does not exist")
        }

        // TODO get the balance
        return 42;
    }
}
