## States

Commit State

![](images/commit_state.png?raw=true)

Reveal State

![](images/reveal_state.png?raw=true)

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
