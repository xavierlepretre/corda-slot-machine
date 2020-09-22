package com.cordacodeclub.webserver

import com.cordacodeclub.flows.*
import com.cordacodeclub.flows.LockableTokenFlows.Fetch.NotEnoughTokensException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(val rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy
    private val quickConfig = proxy.startFlow(::GetNotaryAndCasino)
            .returnValue.getOrThrow()

    @PostMapping(value = ["/create"], produces = ["text/plain"])
    private fun create(request: HttpServletRequest): ResponseEntity<String> {
        val name = request.getParameter("name")
        return try {
            val player = proxy.startFlow(UserAccountFlows.Create::Initiator, name)
                    .returnValue.getOrThrow()
                    .second
            proxy.startFlow(LockableTokenFlows.Issue::InitiatorBeg,
                    LockableTokenFlows.Issue.Request(quickConfig.notary, player, quickConfig.casinoHost))
                    .returnValue.getOrThrow()
            val balance = proxy.startFlow(LockableTokenFlows.Balance::Local, player, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(balance.toString())
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you need to reset")
        } catch (error: AccountAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Account $name already exists")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @GetMapping(value = ["/balance"], produces = ["text/plain"])
    private fun balance(@RequestParam(value = "name") name: String): ResponseEntity<String> {
        return try {
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(balance.toString())
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you need to reset")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    @PostMapping(value = ["/spin"], produces = ["application/json"])
    private fun spin(request: HttpServletRequest): ResponseEntity<Any> {
        val name = request.getParameter("name")
        val wager = request.getParameter("wager").toLong()
        return try {
            val result = proxy.startFlow(GameFlows::SimpleInitiator, quickConfig.notaryName,
                    name, wager, quickConfig.casinoHost, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.ok(SpinResult(result).copy(balance = balance, last_win = result.payout_credits))
        } catch (error: AccountNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Account not found, you may want to reset")
        } catch (error: NotEnoughTokensException) {
            val balance = proxy.startFlow(LockableTokenFlows.Balance::SimpleLocal, name, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Not enough tokens to spin for $wager. Balance: $balance")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

    // the following are just for testing, not used in production

    @GetMapping(value = ["/test"], produces = ["text/plain"])
    private fun test(): String {
        return "Test OK"
    }

    @PostMapping(value = ["/echo"], produces = ["text/plain"])
    private fun echo(request: HttpServletRequest): ResponseEntity<String> {
        val payload = request.getParameter("payload")
        return ResponseEntity.ok("Echo $payload")
    }

    @PostMapping(value = ["/payout"], produces = ["text/plain"])
    private fun spinPayout(request: HttpServletRequest): ResponseEntity<String> {
        val name = request.getParameter("name")
        val wager = request.getParameter("wager").toLong()
        return try {
            val result = proxy.startFlow(GameFlows::SimpleInitiator, quickConfig.notaryName, name,
                    wager, quickConfig.casinoHost, quickConfig.casinoHost)
                    .returnValue.getOrThrow()
            // returns a single simple element from the GameResult
            ResponseEntity.ok("Created ${result.payout_credits}")
        } catch (error: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed $error")
        }
    }

}