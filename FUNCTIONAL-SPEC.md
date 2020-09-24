# Functional specification

- [Customer requirements](#customer-requirements)
- [Requirement analysis](#requirement-analysis)
- [Network architecture](#network-architecture)
- [Commit-reveal](#commit-reveal)
  - [Why is the outcome of the game a secret?](#why-is-the-outcome-of-the-game-a-secret)
  - [Why not use a secret from a single source?](#why-not-use-a-secret-from-a-single-source)
  - [How to publish several secrets simultaneously?](#how-to-publish-several-secrets-simultaneously)
- [Lockable tokens](#lockable-tokens)
  - [Why not use regular tokens?](#why-not-use-regular-tokens)
  - [How do lockable tokens work?](#how-do-lockable-tokens-work)
  - [What about an escrow account?](#what-about-an-escrow-account)
- [Foreclosing after a timeout](#foreclosing-after-a-timeout)
  - [What does the normal flow ("happy path") look like?](#what-does-the-normal-flow-happy-path-look-like)
  - [What does a "foreclosure" flow look like?](#what-does-a-foreclosure-flow-look-like)
- [Preventing malicious timeouts](#preventing-malicious-timeouts)
  - [What if the Casino doesn't reveal after the commit is notarised?](#what-if-the-casino-doesnt-reveal-after-the-commit-is-notarised)
  - [What if the Player doesn't send the notarised commit to the Casino?](#what-if-the-player-doesnt-send-the-notarised-commit-to-the-casino)
  - [What if the Casino has cancelled?](#what-if-the-casino-has-cancelled)
  - [What if the Casino doesn't send its reveal to the Player?](#what-if-the-casino-doesnt-send-its-reveal-to-the-player)
  - [What if the Player doesn't send its reveal to the Casino?](#what-if-the-player-doesnt-send-its-reveal-to-the-casino)
- [List of timers and commands](#list-of-timers-and-commands)
- [The entire normal flow](#the-entire-normal-flow)
  - [The commit phase](#the-commit-phase)
  - [The reveal phase](#the-reveal-phase)
  - [The finalise phase](#the-finalise-phase)
- [External interfaces](#external-interfaces)
  - [Issuing tokens](#issuing-tokens)
  - [Web server](#web-server)
  - [Initiating flows](#initiating-flows)
- [Summary](#summary)
  - [Contracts](#contracts)
  - [Verification](#verification)
  - [Last words](#last-words)

The structure of this document is as follows:

- Three high-level, introductory sections -- i.e. the requirements, analysis, and architecture
- Two sections which introduce the "happy path" -- i.e. "commit-reveal", and "lockable tokens"
- Two sections which explain the "unhappy paths" -- i.e. "foreclosure", and various "timeouts"
- Two sections which summarise the above -- i.e. "timers and command", and the "entire normal flow"
- One section which describes the system's external interfaces or I/O -- i.e. "issuing tokens", the "web server",
  the flows initiated by the web server, and the "game logic"
- A final summary

## Customer requirements

The "customer" requirements that we were given were brief:

- To use Corda to implement a slot-machine
- To demonstrate it at CordaCon 2020

We were given a copy of a 3rd-party Web UI from https://slotmachinescript.com/ to integrate.

## Requirement analysis

We wanted to make this a good demonstration of Corda's strengths --
to implement cooperation, between parties which trust Corda, but do not trust each other.
So the design of the CorDapp is meant to be fool-proof or cheat-proof,
i.e. impossible for either party to cheat.

We identify various threats, as follows,
which the design (this functional specification) is intended to safeguard against.

Threat|Safeguard
---|---
Game result isn't random|Both parties contribute to each game's randomness
Loser cannot or will not pay at game's end|Tokens are associated with each game in advance
A party quits play as soon as it discovers it will lose|The game contract allows the winning party to unilaterally collect their winnings after a timeout
A party withholds data to cause the other party to timeout|Careful reasoning about the sequence of the "reveal"

## Network architecture

We want the simplest realistic network, for the demo or the proof of concept.

- The CorDapp runs on two Corda nodes:

  - A "Casino" node which implements/emulates the owner of the slot machine
  - A "Players" node which is used by players (end-users) of the slot machine

  It's assumed that players (end-users) control and can inspect and trust the CorDapp code which is running on the
  "Players" node, and don't especially trust the "Casino" node.

- There's one other, separate, Notary node.

- The Web UI requires a web server, and the end-users' browsers.
  There's one web server for all users.
  It connects to (i.e. it uses RPC to initiate flows on) the "Players" node.

## Commit-reveal

In theory the outcome of each game should be random -- i.e. based on a random number --
with the odds of winning (and the amount of each type of win) being predefined in the contract.

### Why is the outcome of the game a secret?

The random number can be considered a "secret" --
it determines the outcome of the game, and it must be kept secret until after the parties are committed to playing --
because if a party knows the secret in advance, then it could choose to play only when it knows it's going to win.

### Why not use a secret from a single source?

One of the threats is the game's not being random.
If the implementation depends on any single source (e.g. a single Corda node) to generate the random number,
then that source might cheat, and tend to pick a number which is favourable to one party.
To avoid this threat:

- Both parties each pick their own random number
- The game contract specifies that the two random numbers are combined (and how they're combined)

Each party can therefore trust that the outcome is random (because they themselves contributed to its randomisation),
and has an incentive to choose a "good" random number (which can't be predicted by the other party).

### How to publish several secrets simultaneously?

This leads to a second problem though --
each party's own random number is a secret, but how can they safely exchange (publish) their secrets "simultaneously"?
The threat is that as soon as a party knows the counter-party's secret, then it might change its own number
before revealing it (to guarantee a win for itself).
To avoid this we implement the "commit-reveal" algorithm --
which is described in Wikipedia's [Commitment scheme](https://en.wikipedia.org/wiki/Commitment_scheme) article.

The "commit-reveal" algorithm has previously been implemented on Ethereum -- but not (so far as we know) on Corda --
so we think it's an appropriate and interesting algorithm to explore for this demo.

The "commit-reveal" algorithm works as follows:

1. Both parties pick their own random number, and keep it secret
2. Both parties generate a hash of their random number
3. Each party publishes the hash (by sending it to the other party)

   Publishing the hash is called the "commit" phase.

4. After each party has the other's hash, the two parties can "reveal" their previously-secret random numbers.

   The contract verifies that each random number matches the previously-published hash.
   This guarantees that a party cannot change its random number after it learns what the other party's random number is.

   Publishing the secrets is called the "reveal" stage.

## Lockable tokens

We implement what we call "lockable tokens" to guard against two threats:

- The losing party cannot afford to pay at the end of the game
- The losing party refuses to pay at the end of the game

Each party assigns some of their own tokens to each game.
At the end of the game, the winner gets their own tokens back, plus some or all of the tokens from the losing party.

### Why not use regular tokens?

The problem with regular tokens -- e.g. as predefined in Corda's token library --
is that these tokens require the current owner's signature, in transactions which reassign ownership of the token to
another party.

This (requiring the owner's signature) is what you normally want in a token --
but not for this use case, where one of the threats we want to guard against is a "sore loser",
who might refuse to sign after they know that they have lost.

### How do lockable tokens work?

A "lockable token" is (like any other token) a Corda state, whose behaviour is controlled by a contract.

- A token is issued (has an issuer)
- A token is normally owned (has an owner), but not always
- The owner can "lock" a token, which means that:

  - The token is no longer owned by its owner
  - The token is instead associated with a game contract

  Locking a token requires the owner's signature.

- The game contract allows a transaction to "release" the locked tokens and assign them to the winner of the game, without requiring the former owner's signature.

  The owner's signature was only required earlier, i.e. to "lock" the token and assign it to the game contract.
  The game's final transaction only requires the signature of the winner, or any signature really as the contract is fully in control.

### What about an escrow account?

Instead of "lockable tokens" we could have decided to use regular tokens with an escrow account:

- Before each game, the parties assign their tokens to an escrow account --
  i.e. to the owner of an escrow account, perhaps on some other node like a "Bank" node
- After each game, the owner of the escrow account automatically assigns the tokens to the winner of the game.

We preferred to implement lockable tokens instead:

- Using an escrow account depends on trusting the owner of that account, to sign correctly (to reassign ownership) 
- Using lockable tokens means that the behaviour at the end of the game depends only on the contract,
  and on the winning party being willing to create the transaction to collect their winnings.

## Foreclosing after a timeout

Another threat is that as soon as a party knows the other's secret, then it knows whether it will win or lose the game.
A losing party might then choose to abandon the game immediately, instead of letting it finish.

To guard against this, we allow the other party to "foreclose" after a timeout -- i.e. to automatically win the game
after a timeout, if the other party doesn't continue in time.

### What does the normal flow ("happy path") look like?

In more detail:

1. Assume that the "commit" phase of the commit-reveal has been completed, and notarised,
   with a notarised copy of the transaction successfully delivered to both parties.
2. The Casino node reveals its secret to the Player node
3. The Player node reveals its secret to the Casino node
4. Whichever is the winning node combines the two secrets,
   into a single final "reveal" transaction which finishes the game. In the current 
   implementation, the player finishes the game.

The reveal is asymmetric i.e. "Casino reveals before the Player" -- for reasons explained later, in the first two
subsections of [Preventing malicious timeouts](#preventing-malicious-timeouts).

### What does a "foreclosure" flow look like?

The threat is that the player might not finish the game after it receives the Casino's secret.
To guard against this we allow the Casino node to foreclose, as follows

1. The commit phase is finished (as above)
2. The Casino node reveals its secret to the Player node
3. The Player node does nothing (hoping to prevent the game's completion)
4. A timeout expires
5. The Casino is now allowed to "foreclose" the game,
   i.e. to create a "foreclosure" transaction which ends the game and gives the winnings to the Casino

## Preventing malicious timeouts

There are various other places on the happy path where one of the parties might interrupt the normal flow,
perhaps maliciously -- i.e. to prevent the game from finishing.

Each of these needs a corresponding safeguard defined.

### What if the Casino doesn't reveal after the commit is notarised?

After the commit is notarised, the Casino is supposed to reveal before the player.
If it doesn't then the Player node should be allowed to timeout and foreclose.

1. The commit phase is finished (as above)
2. The Casino node does nothing (hoping to prevent the game's completion)
3. A timeout expires
4. The Player is now allowed to "foreclose" the game,
   i.e. to create a "foreclosure" transaction which ends the game and gives the winnings to the Player

Even without this timeout being specified, the Casino has no obvious incentive to stall the game --
because the Casino reveals first, at this stage the Casino doesn't know the Player's secret and therefore
cannot know that it's about to lose the game.

To further incentivise revealing, we could associate additional locked tokens that are released on revealing.

### What if the Player doesn't send the notarised commit to the Casino?

The threat here is:

1. The Player notarises the commit transaction
2. The Player doesn't send a copy of the notarised transaction to the Casino
3. The Casino doesn't reveal its secret
   (because it doesn't have a notarised copy of the game to associate with its secret)
4. After a timeout the Player can foreclose the game as defined previously

To guard against this, the Casino is allowed to cancel the game, if it doesn't receive a notarised copy of the game
within a reasonable time of its sending its commit hash.

1. The Casino sends a locked token and a commit hash (during the commit phase)
2. The Player may or may not create and notarise the commit transaction, but in any case doesn't send it to Casino
3. Before the timeout which would allow the Player to foreclose, the Casino cancels the game.

To cancel the game, the Casino simply releases the tokens which it had committed to the game:

- If the Player hadn't yet notarised the commit transaction, then the commit will fail -- because the Casino's
  input tokens have been cancelled, i.e. the "locked" state of the Casino's tokens (attached as input to the commit
  transaction) was consumed when the Casino cancelled that.
- If the Player has already notarised the commit transaction, then the Player's "foreclosure" transaction will fail --
  for the same reason as above, i.e. the Casino's token is cancelled.

### What if the Casino has cancelled?

Eventually the Player's token must be refunded too, after the Casino cancels.
We implement this by defining a long timer -- i.e. some long time after the game should have ended
(i.e. ended by someone's winning or foreclosing), the Player is allowed to cancel.

This is not a very "happy path", but at least every party gets their tokens refunded (i.e. unlocked) eventually.

### What if the Casino doesn't send its reveal to the Player?

The threat here is:

- The commit transaction is notarised and distributed
- The Casino notarises its reveal, but doesn't send its notarised reveal to the Player
- The Player does nothing
- The Casino forecloses after a timeout

To guard against this, the Player should notarise its own reveal without waiting for a reveal from the Casino.
This reveal will consume the Player's copy of the game state and prevent the Casino's foreclosing.
The player should not send its notarised reveal to the Casino.

### What if the Player doesn't send its reveal to the Casino?

The threat here is:

- Both parties have notarised their reveals, so neither can foreclose
- The Casino has sent its reveal to the Player (but not vice versa)
- The Player refuses to send its notarised reveal to the Casino

In this case the Casino can see that the Player has notarised -- because the Casino's attempt to foreclose will fail.
The Casino can't see what the reveal is, however (the Player and the Notary both doesn't, but the Casino doesn't).

There isn't a very good, application-specific solution to this problem!

It's an instance of a more general problem with Corda, i.e. "What happens when the ledger is inconsistent, because one
of the nodes doesn't distribute newly-notarised state to the other nodes concerned?"

## List of timers and commands

For reference, here's a list of the timers defined in the specifications above.

- "Casino can foreclose" -- some time after the Casino has revealed, if the Player doesn't also reveal
- "Player can foreclose" -- some time after the commit is notarised, if the Casino hasn't revealed
- "Casino can cancel" -- some time after the Casino has committed, before the Casino has revealed
- "Player can cancel" -- a long time after the game should have ended

Here is a list of transaction types i.e. commands:

- On the happy path:

  - "Atomic commit and lock" -- locks tokens from both parties, commits hashes from both parties and creates a game state,
  - "Casino reveal" -- consumes the Casino's commit state and creates the Casino's revealed state,
    prevents the Casino's cancelling and prevents the Player's foreclosing
  - "Player reveal" -- consumes the Player's commit state and creates the Player's revealed state,
    prevents the Casino's foreclosing
  - "Finalise" -- combines the two reveals, calculates the winner and the amount of winnings,
    and unlocks the tokens in favour of the winner.

- On the unhappy path:

  - "Casino cancel" (if the player doesn't commit)
  - "Player cancel" (if the casino cancels)
  - "Casino foreclose" (if the player doesn't reveal)
  - "Player foreclose" (if the casino doesn't reveal)

## The entire normal flow

For reference the complete specification of the normal flow looks something like this.

### The commit phase

This involves two commands (two transactions), i.e. "Casino lock" and "Commit".

1. Player initiates a game flow
2. Casino responds:

   - Generates a commit/reveal pair (i.e. a random number and its hash)
   - Identifies and locks a token to commit to the game
   - Returns its locked token and its commit hash to the Player
   - Starts its "Casino can cancel" timer

   The Casino locks its own token before it sends it to the Player.
   This is so that the Casino has the locked in its own copy of the ledger,
   which the Casino can later use to cancel the game,
   even if the Player doesn't deliver a copy of the notarised "Commit" transaction

3. Player prepares its own input:

   - Generates a commit/reveal pair (i.e. a random number and its hash)
   - Locks a token to commit to the game

4. Player creates a "Commit" transaction:

   - The inputs are the tokens and the commit hashes, from the Player and from the Casino
   - The outputs are locked tokens, and two copies of the game state

5. Player finalizes the transaction:

   - Only the Player need sign
   - The Casino need not sign because its input token is already locked, therefore isn't changing state
   - Gets the notary to notarise the transaction
   - Sends the notarised transaction to the Casino
   - Starts its "Player can cancel" timer

### The reveal phase

This starts after the "Commit" transaction is notarised and distributed to both parties. 

- The Casino reveals:

  - Cancels its "Casino can cancel" timer
  - Notarises its reveal
  - Send its reveal to the Player
  - Starts its "Casino can foreclose" timer

- The player also reveals but only privately (before it receives the Casino's reveal):

  - Notarises its reveal
  - Starts its "Player can foreclose" timer
  - Doesn't send its reveal to the Casino

- The player sends its notarised reveal to the Casino, but only after it receives the Casino's reveal.

Each reveal prevents the other party's being able to foreclose successfully.

The reason why the Player doesn't send its reveal to the Casino is that the Casino (unlike the Player)
has a cancel timer which allows it to cancel the game.
Therefore the Casino mustn't be allowed to see a copy of the Player's reveal until after the Casino
is known to have stopped its cancel timer (i.e. after the Casino has revealed).

### The finalise phase

This starts after the "Reveal" transactions are notarised and distributed to both parties. 

- Both parties determine who the winner was
- The winner creates, signs, and notarises the "Finalise" transaction, and sends the notarised transaction to the
  counter-party.

## External interfaces

Previous sections defined the CorDapp's behaviour -- its internal flows and states.

The following subsections define the CorDapp's external interfaces or I/O.

### Issuing tokens

The "Lockable tokens" used in the game must be issued by a party.

In theory we could introduce another node, a "Bank" node, whose responsibility is only to issue tokens.
Instead, to keep it as simple as possible, in the demo the tokens are all issued by the Casino node.

- The Casino can issue itself as many token as it needs
- Each new end-users is automatically issued an initial store of 100 tokens, when they first begin to play.

### Web server

The original web-based slot machine implementation had the gaming logic (e.g. a random number generator) embedded in the
web server, and persistent state (e.g. the account balance for each player) stored in SQL.

This being a Corda demo, we removed that from the web server:

- Game logic is in the CorDapp -- especially in the contract which determines the outcome of the game, i.e. the
  disposition of locked tokens
- Game state is persisted in the Corda ledger

The web server is therefore simple:

- It serves the same static web page to each end-user -- HTML, CSS, and JavaScript.
  The HTML is not customised by the web server before it's served -- it could be served from a CDN.
- The JavaScript customises the appearance of the page and interacts with the end-user -- the web app is a
  [Single-page application](https://en.wikipedia.org/wiki/Single-page_application)
- The web app uses Ajax to transact with the web server
- The web server is thin, its REST end-points delegate to corresponding Corda flows.

There are three REST end-points:

- Create a new account
- Get the balance of an existing account
- Spin (i.e. play a game and return the result)

The code for these is in [`Controller.kt`](./clients/src/main/kotlin/com/cordacodeclub/webserver/Controller.kt) --
you can see how thin these methods are, they simply use `proxy.startFlow` to delegate to corresponding Corda flows.

There are further details about the web implementation -- irrelevant to Corda -- in
[`README.CLIENT.md`](./README.CLIENT.md).

### Initiating flows

There are four flows which may be initiated by the web server:

- `UserAccountFlows.Create.Initiator` -- defined in
  [`UserAccountFlows.kt`](workflows/src/main/kotlin/com/cordacodeclub/flows/UserAccountFlows.kt)
- `LockableTokenFlows.Issue.InitiatorBeg` -- defined in [`LockableTokenFlows`](workflows/src/main/kotlin/com/cordacodeclub/flows/LockableTokenFlows.kt), which allows the new player to be issued 100 tokens
- `LockableTokenFlows.Balance.Local` and `LockableTokenFlows.Balance.SimpleLocal` -- defined  in [`LockableTokenFlows`](workflows/src/main/kotlin/com/cordacodeclub/flows/LockableTokenFlows.kt)
- `GameFlows.SimpleInitiator` -- defined in
  [`GameFlows.kt`](workflows/src/main/kotlin/com/cordacodeclub/flows/GameFlows.kt), which orchestrates the whole commit-reveal and resolve process across multiple transactions

## Summary

### Contracts

Previous sections above describe ...

- The states
- The flows
- The transactions (commands)
- The timeouts

... associated with the normal and abnormal flows (the happy and unhappy paths).

Together with the definition of the architecture, and of the CorDapp's other external interfaces,
we hope this is sufficient as a high-level functional specification.

This level of design is necessary but not sufficient.
In particular a hostile node might replace the CorDapp's normal flow, with another flow designed to exploit any
weakness.
To prevent such an exploit, CorDapp requires developers to define a "contract" -- as well as designing states and flows.

The design of the contract may be considered "detailed design" rather than "high-level design".
In any case the contracts are not defined in this Functional Specification, but may be inferred from it.

### Game contract logic

The odds of winning are defined in the contract.

Defined in [`GameContract`](contracts/src/main/kotlin/com/cordacodeclub/contracts/GameContract.kt) and [`CommitImage`](contracts/src/main/kotlin/com/cordacodeclub/states/CommitStates.kt)

### Verification

- For contracts, we implemented extensive unit tests.
- For flows, we tested the happy path and a couple of un-happy paths via the use of broken game flows.

- That this specification is sufficient i.e. threat-proof?
- That the CorDapp is implement as specified?

### Performance

On a reasonable development computer, we have observed the speed of a game flow to be between 2 and 2.5 seconds.

On a 2-AWS instances setup, we have observed the speeds of a game flow to be between 1 and 1.5 seconds.

### Last words

- This was interesting to implement.
- There's significant (complex) functionality in the CorDapp, hidden behind a simple/narrow API defined by the
  initiating flows
- The commit-reveal algorithm is neat in principle.
- Almost all of the complication comes from guarding against the threat that one of the parties might try to quit
  during the reveal phase i.e. after the party knows that it is going to lose -- this required lockable tokens
  which can unlock automatically, and timers for foreclosure and cancellation
- The fully-safeguarded happy path is complicated -- it appears to require five notarised transactions for each game!
- With careful design we're able make the game robust, even when one of the parties tries to quit the game
  - Parties can cancel or foreclose the game, using only input states which already exist on their own ledger
  - The notary (and our carefully defining which input states are required by which transactions) arbitrates the race
    conditions which can happen when the two parties try to transact independently and simultaneously
- The one threat or hole that we weren't able to guard against seems to be inherent to Corda --
  i.e. when one of the parties doesn't deliver a notarised transaction to the other party -- see
  [What if the Player doesn't send its reveal to the Casino?](#what-if-the-player-doesnt-send-its-reveal-to-the-casino)

  This design does the best it can and can do no better in this case --
  the design ensures that both parties will have notarised their reveals.