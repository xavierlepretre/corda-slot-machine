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
