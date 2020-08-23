package com.cordacodeclub.webserver

import com.cordacodeclub.flows.CreateUserAccount
import com.cordacodeclub.flows.GetUserBalance
import com.cordacodeclub.flows.InitiatePlayGame
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PostMapping(value = ["/create"], produces = ["text/plain"])
    private fun create(request: HttpServletRequest): ResponseEntity<String> {
        val name = request.getParameter("name")
        try {
            val balance = proxy.startFlow(::CreateUserAccount, name).returnValue.getOrThrow()
            return ResponseEntity.ok(balance.toString())
        } catch (e: Exception) {
            val error = e.toString()
            return ResponseEntity.ok("Failed $error")
        }
    }

    @GetMapping(value = ["/balance"], produces = ["text/plain"])
    private fun balance(@RequestParam(value = "name") name: String): ResponseEntity<String> {
        try {
            val balance = proxy.startFlow(::GetUserBalance, name).returnValue.getOrThrow()
            return ResponseEntity.ok(balance.toString())
        } catch (e: Exception) {
            val error = e.toString()
            return ResponseEntity.ok("Failed $error")
        }
    }

    @PostMapping(value = ["/spin"], produces = ["application/json"])
    private fun spin(request: HttpServletRequest): ResponseEntity<SpinResult> {
        val name = request.getParameter("name")
        try {
            val result = proxy.startFlow(::InitiatePlayGame, name).returnValue.getOrThrow()
            return ResponseEntity.ok(SpinResult(result))
        } catch (e: Exception) {
            val error = e.toString()
            return ResponseEntity.ok(SpinResult("Failed $error"))
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
        try {
            val result = proxy.startFlow(::InitiatePlayGame, name).returnValue.getOrThrow()
            // returns a single simple element from the GameResult
            return ResponseEntity.ok("Created ${result.payout_credits}")
        } catch (e: Exception) {
            val error = e.toString()
            return ResponseEntity.ok("Failed $error")
        }
    }

}