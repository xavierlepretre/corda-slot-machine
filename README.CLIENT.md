Here are notes about the web server in the ./clients directory.

This is based on https://github.com/corda/cordapp-template-kotlin.git

## Web server

There's a useful (i.e. complete) example of a web server in the
https://github.com/corda/samples-kotlin/tree/master/Basic/cordapp-example project.

## Introduction

Here are some introductions to Spring:

- https://www.corda.net/blog/spring-cleaning-migrating-your-cordapp-away-from-the-deprecated-corda-jetty-web-server/
- https://spring.io/guides/gs/spring-boot/ (build a simple app in 15 minutes)

And to Corda RPC clients in general

- https://docs.corda.net/docs/corda-os/4.5/clientrpc.html

This includes configuring RPC users' credentials, and permissions to use specific flows. 

## Jackson

The `Server.kt` file in the `cordapp-example` project (i.e. [here](https://github.com/corda/samples-kotlin/blob/master/Basic/cordapp-example/clients/src/main/kotlin/com/example/server/Server.kt)) defines a
`mappingJackson2HttpMessageConverter` function.
The blog says,

> Since Corda uses a Jackson object type mapping we need to define a Java Spring @Bean to
bind the Corda Jackson object-mapper to the HTTP message types used by Spring.

This function isn't present in the `spring-webserver` example nor in the
`cordapp-template-kotlin` template.

I believe it's for mapping some complex types, like "date" or "party" perhaps.

The current implementation works alright without this function,
presumably because I don't use Corda-specific types in the RPC interfaces
-- e.g. in the `GameResult` type.

## How to start it

https://docs.corda.net/docs/corda-os/4.5/tutorial-cordapp.html explains how to start it:

- To rebuild and restart the nodes:

      ./gradlew build
      ./gradlew deployNodes
      ./build/nodes/runnodes

- To start the web server:

      ./gradlew runPartiesServer
  
  This also builds (doesn't just start) the web server.
  After running this there's eventually a message like ...
  
      [INFO ] 23:25:29.121 [main] TomcatEmbeddedServletContainer.start - Tomcat started on port(s): 50005 (http)
      
  ... after which you can connect with a web browser e.g. to http://localhost:50005/
  
  In other projects, what I named the `runPartiesServer` task might be named `runPartyAServer` or `runTemplateServer`.

See also
https://github.com/corda/samples-kotlin/blob/master/Basic/spring-webserver/README.md
which says how to start it without Gradle,
i.e. by building and running a JAR,
or by using an IntelliJ run configuration.

## Source code

The slot machine UI is third-party `slots_v2.0.0.zip` from https://slotmachinescript.com/ which contains:

- A `readme.html`
- Web application server source code, written in PHP
- JavaScript, implementing in-browser front-end logic and DOM manipulation --
the "spin" action uses Ajax, so it's all a simple "single-page application", except perhaps for the login
- Image files and CSS
- SQL files

To integrate this we:

- Discard the PHP and SQL code
- Keep the front end JavaScript, CSS and Images (preferably unaltered)
- Reimplement the webserver using Spring Boot
- Delegate to RPC end points for the back end
- Move Game logic into the CorDapp contract, especially calculating payout values

The `readme.html` is [located here](./clients/readme.html) where it won't be seen by end-users.
It contains interesting details about how to customise the game.

I didn't archive the original `slots_v2.0.0.zip` in this repository (ask Martin Jee for a copy of it if you want one).

Unfortunately there's no obvious way to run PHP on Java.
Solutions for combining PHP with Spring are unsupported (e.g. dated 2014):

- https://stackoverflow.com/questions/7068681/mixing-spring-mvc-with-inline-php
  suggests http://quercus.caucho.com/
  for "Java implementing PHP"
- https://stackoverflow.com/questions/19310519/any-spring-php-project
  suggests https://code.google.com/archive/p/springphp/
  for "Porting Spring to PHP"

So there are two alternatives:

- Keep (and deploy) the PHP implementation, and connect it to
  our Corda RPC end-points using [LAB577's Braid](https://gitlab.com/bluebank/braid)
- Reimplement using Java (e.g. Spring) to connect to Corda's RPC directly i.e. using a `CordaRPCConnection` instance

I chose the latter:

- Because after moving game logic into Corda, there's not much source code left in the web server
  (so not too difficult to port)
- Because an all-Java (or all-Kotlin) solution might be easier for other Corda developers to support,
  (e.g. doesn't require deploying to a PHP environment/stack)

Having chosen Spring there are then two ways to replace the PHP (which implements server-side rendering):

- Choose some other server-side template engine for Spring --
  [Comparing Template engines for Spring MVC](https://github.com/jreijn/spring-comparing-template-engines)
  lists 20 of them, including JSP, Groovy, and Mustache
  -- see also https://www.baeldung.com/spring-template-engines
- Serve static HTML with client-side rendering --
  this is what https://github.com/corda/samples-kotlin/tree/master/Basic/cordapp-example is doing,
  using Angular as its JavaScript framework

Again, I chose the latter, to minimise the required dependencies -- i.e. a template engine (syntax and implementation).
Client-side rendering will be more maintainable fwiw, because every web developer is familiar with JavaScript.

The pre-existing 3rd-party JavaScript (i.e. `js/slots.js`) depends on `jquery` and `jquery-ui` libraries
(and not e.g. Angular or React).

To avoid touching the 3rd-party `js/slots.js` I do the following:

- Write a static HTML file (i.e. `slots/index.html`) to emulate the HTML embedded in the PHP
- Write a JavaScript file (i.e. `js/setup.js`) to emulate the way in which the PHP would have customised the HTML --
  this runs in the client's browser and edits the DOM before the 3rd-party `slots.js` is run

  All scripts are included at the end of the HTML, so `setup.js` simply runs immediately
instead of being triggered by the `$(window).on("load")` event.
This requires an extra round-trip to the server (to get user ID and balance after the static HTML is loaded),
though this whole solution is already unoptimized e.g. with multiple script files and image files to load.
This will off-load the server a bit anyway
(i.e. not running a templating engine on the server, the static HTML ought to be cacheable).

## Testing

There are no automated tests (i.e. regression tests).

You can run end-to-end manually as described above (see [How to start it](how-to-start-it)).

There's also a test page -- see
[index.html](clients/src/main/resources/static/index.html) and
[app.js](clients/src/main/resources/static/app.js) --
which you can use to invoke and visually inspect the results of calls to the web server,
without using the 3rd-party slot-machine front end.

## Login

We need an account for each end-user.
Storing a persistent user-name is a nuisance per the GDPR.

I propose the following as a solution which I hope avoids the need for us to implement storing and managing the user's consent:

- When the app loads, it checks for presence of a cookie.
- If the cookie is present then the account name is extracted from the cookie and the user can play.
- If no cookie is present then an account must be created.
The account always has a random name.
If the user selects "Remember me" then the generated random name is also stored as a cookie.
I'm not a lawyer but perhaps this counts is as an essential session cookie,
instead of as a preference cookie for which we need consent.
- The user can clear the cookie at any time.