ssh -L 10004:127.0.0.1:10003 ubuntu@54.216.255.188

# Corda Shell
# corda-shell --host=127.0.0.1 --port=10004 --user=rpcuser --password=ChangeTh1sPa$$w0rd

# Gradlew for runPartiesServer
#task runPartiesServer(type: JavaExec, dependsOn: assemble) {
#    classpath = sourceSets.main.runtimeClasspath
#    main = 'com.cordacodeclub.webserver.ServerKt'
#    args '--server.port=8080', '--config.rpc.host=localhost', '--config.rpc.port=10003', '--config.rpc.username=rpcuser', '--config.rpc.password=ChangeTh1sPa$$w0rd'
#}