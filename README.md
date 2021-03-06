
## Quick links

States:

* [GameState](contracts/src/main/kotlin/com/cordacodeclub/states/GameState.kt) / [GameContract](contracts/src/main/kotlin/com/cordacodeclub/contracts/GameContract.kt)
* [CommitState](contracts/src/main/kotlin/com/cordacodeclub/states/CommitStates.kt) + hash calculation + payout calculation / [CommitContract](contracts/src/main/kotlin/com/cordacodeclub/contracts/CommitContract.kt)
* [LockableTokenState](contracts/src/main/kotlin/com/cordacodeclub/states/TokenStates.kt) / [LockableTokenContract](contracts/src/main/kotlin/com/cordacodeclub/contracts/LockableTokenContract.kt)
* [LeaderboardEntryState](contracts/src/main/kotlin/com/cordacodeclub/states/LeaderboardStates.kt) / [LeaderboardEntryContract](contracts/src/main/kotlin/com/cordacodeclub/contracts/LeaderboardEntryContract)

Flows:

* Orchestrating [GameFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/GameFlows.kt)
* Inlined [CommitFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/CommitFlows.kt)
* Inlined [RevealFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/RevealFlows.kt)
* Inlined [ResolveFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/UseFlows.kt)
* Unhappy paths [ForeClosureFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/ForeClosureFlow.kt) / and the [BrokenGameFlows](workflows/src/main/kotlin/com/cordacodeclub/flows/BrokenGameFlows.kt) to test that
* Flows for [LockableTokens](workflows/src/main/kotlin/com/cordacodeclub/flows/LockableTokenFlows.kt)
* [UserAccountFlows](workflows/src/main/kotlin/com/cordacodeclub/flows/UserAccountFlows.kt)
* [LeaderboardFlow](workflows/src/main/kotlin/com/cordacodeclub/flows/LeaderboardFlows.kt)

Other:

* [NicknamesDatabaseService](workflows/src/main/kotlin/com/cordacodeclub/services/LeaderboardNicknamesDatabaseService.kt)
* [API controller](clients/src/main/kotlin/com/cordacodeclub/webserver/Controller.kt)

## Run in CLI

It is possible to simulate a game from the nodes shells.

First prepare, in the project's root folder:

```shell
$ ./gradlew deployNodes
$ ./build/nodes/runnodes
```

Then, play:

1. Issue some tokens for casino. On Casino node:

    ```shell
    >>> flow start com.cordacodeclub.flows.LockableTokenFlows$Issue$Initiator notary: Notary, holder: Casino, amount: 100000, issuer: Casino
    ```
2. Create a player account and optionally inform the Casino. On Parties node:

    ```shell
    >>> flow start com.cordacodeclub.flows.UserAccountFlows$Create$Initiator accountName: player1, observer: Casino
    ```
3. Beg for some tokens from casino. On Parties node:

    ```shell
    >>> flow start com.cordacodeclub.flows.LockableTokenFlows$Issue$InitiatorBegSimple notary: Notary, holderAccountName: player1, issuer: Casino
    ```
    Or have the casino issue you as much as you want. On Casino node:
    
    ```shell
    >>> flow start com.cordacodeclub.flows.LockableTokenFlows$Issue$SimpleInitiator notary: Notary, holderAccountName: player1, amount: 100000, issuer: Casino
    ```
4. Start a game from player. On Parties node:

    ```shell
    >>> flow start com.cordacodeclub.flows.GameFlows$SimpleInitiator playerAccountName: player1, playerWager: 2, issuer: Casino, casino: Casino
    ```
5. See how many tokens the player has. On Parties node:

    ```shell
    >>> flow start com.cordacodeclub.flows.LockableTokenFlows$Balance$SimpleLocal holderName: player1, issuer: Casino
    ```
6. See how many tokens the casino has. On Casino node:

    ```shell
    >>> flow start com.cordacodeclub.flows.LockableTokenFlows$Balance$Local holder: Casino, issuer: Casino
    ```

## Explainers

![Random number generation](images/1-random-number-to-reward.jpg)

![Commit reveal steps](images/2-commit-reveal.jpg)

![Tx transitions happy path](images/3-tx-transitions-happy-path.jpg)

![Game flow happy path](images/4-game-flow-happy-path.jpg)