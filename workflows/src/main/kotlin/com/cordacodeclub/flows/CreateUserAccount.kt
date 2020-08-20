package com.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

@InitiatingFlow
@StartableByRPC
class CreateUserAccount(private val accountName: String) : FlowLogic<String>() {

  @Suspendable
  override fun call(): String {

    if (accountService.accountInfo(accountName).any() { it.state.data.host == ourIdentity }) {
      return "$accountName account already exists"
    }

    //Call inbuilt CreateAccount flow to create the AccountInfo object
    return try {
      val account = subFlow(CreateAccount(accountName))
      "$accountName account has been created"
    } catch (error: Exception) {
      "Exception: $error"
    }
  }
}
