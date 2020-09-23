#!/bin/bash

# This script will download and install Corda to your machine using the one time key provided;
#
# Requirements;
# 1. ONE_TIME_DOWNLOAD_KEY environment variable this can be obtained when creating a new node
# 2. java 1.8 or higher (script will attempt to install if user is super user)
# 3. unzip
# 4. screen
#
# Optional requirements;
# apt/apt-get to auto-install dependencies

installDir=/opt/corda
nodeZipLocation=/opt/corda/node.zip
jdkPackage=openjdk-8-jre
cordaUser=corda
cordaNetwork=TESTNET
lockFilename=.installer.lock
nodeCertLocation=/opt/corda/certificates/nodekeystore.jks
ip="0.0.0.0" # See getPublicHost
x500O=""
x500OU=""
includeWebServer=false
useOracleJdk=false

set -eu

deleteLockfileIfCertsExist() {
    if [[ ! -f $nodeCertLocation ]]; then
        rm -f $lockFilename
    fi
}

checkUserIsRoot() {
    echo "Checking if current user is root"
    if (( $EUID == 0 )); then
        echo "User is root, proceeding"
    else
        echo "Please run this script as root"
        exit 1
    fi
}

checkEnvironmentVars() {
    echo "Checking if environment variables are set"
    if [ -z "$ONE_TIME_DOWNLOAD_KEY" ]; then
        echo "Environment variable ONE_TIME_DOWNLOAD_KEY is not set and is required to install Corda from Corda TestNet"
        exit 1
    fi
    echo "Environment variables are all set"
}

checkJava() {
    echo "Verifying that Java is installed"
    if type -p java; then
        echo "Found Java on path"
        javaBin=java
    elif [[ ! -z ${JAVA_HOME:-} ]] && [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
        echo "Found JAVA_HOME at $JAVA_HOME, using $JAVA_HOME/bin/java"
        javaBin="$JAVA_HOME/bin/java"
    else
        echo "Java not found - attempting Java install"
        attemptJavaInstall
        javaBin=java
    fi

    checkJavaVersion
}

# Requires the package to have the same name as the executable (Eg screen, wget)
ensurePackageIsInstalled() {
    echo "Ensuring package $1 is installed"
    if ! dpkg -s $1 > /dev/null 2>&1; then
        attemptPackageInstall $1
    else
        echo "Package $1 is already installed"
    fi
}

attemptJavaInstall() {
    if [[ "$useOracleJdk" = true ]]; then
        echo "Adding Oracle JDK 8 repository"
        add-apt-repository ppa:webupd8team/java

        echo "Installing Oracle JDK 8..."
        attemptPackageInstall oracle-java8-installer
    else
        attemptPackageInstall ${jdkPackage}
    fi
}

attemptPackageInstall() {
    if type -p apt; then
        echo "Installing package $1"
        apt -qq update
        apt -qq -y install $1
    elif type -p apt-get; then
        echo "Installing package $1"
        apt-get -qq update
        apt-get -qq -y install $1
    else
        echo "$1 was not found and could not install via apt or apt-get, please install $1 first"
        exit 1
    fi
}

checkJavaVersion() {
    echo "Checking Java version"
    if [[ ${javaBin} ]]; then
        version=$("$javaBin" -version 2>&1 | awk -F '"' '/version/ {print $2}')
        if [[ "$version" > "1.8" || "$version" == "1.8" ]]; then
            echo "Java version meets minimum requirements (1.8)"
        else
            echo "Java version does not meet minimum requirements of 1.8. Found: $version"
            exit 1
        fi
    fi
}

createUser() {
    echo "Creating user with username $cordaUser"
    if type -p adduser; then
        if ! id -u corda; then
            adduser --system --group --no-create-home --shell "/bin/sh" --disabled-password "$cordaUser"
            echo "User created"
        else
            echo "User Corda already exists"
        fi
        userCreated=true
    else
        echo "Could not create user, using root or current user (for msys instead)"
        userCreated=false
    fi
}

checkDirectory() {
    echo "Installing to $installDir"
    if [ ! -d ${installDir} ]; then
        echo "Directory does not exist, attempting to create"
        mkdir ${installDir}
    elif [ ! -w ${installDir} ]; then
        echo "Current user does not have write permissions to  $installDir"
        exit 1
    fi

    if [ ${userCreated} == true ]; then
        echo "Setting directory owner to corda"
        chown "$cordaUser:$cordaUser" ${installDir}
    fi
}

downloadNode() {
    echo "Generating and downloading node - this may take several minutes depending on your connection"
    # Developer note: http://onboarder.prod.ws.r3.com is replaced on server side at runtime
    # TODO: Use user input here
    oPart=''
    ouPart=''

    if [[ ! -z "$x500O" ]]; then
        oPart=', "organisation": "'"$x500O"'"'
    fi

    if [[ ! -z "$x500OU" ]]; then
        ouPart=', "organisationUnit": "'"$x500OU"'"'
    fi

    curl -L -d '{"x500Name":{"locality":"London", "country":"GB"'"$oPart"''"$ouPart"'}, "config": { "ipAddress": "'"$ip"'" }}' \
    -H "Content-Type: application/json" \
    -X POST "http://onboarder.prod.ws.r3.com/api/user/node/generate/one-time-key/redeem/$ONE_TIME_DOWNLOAD_KEY" \
    -o "$nodeZipLocation"

    zipSize=$(stat -c %s /opt/corda/node.zip)

    if [ "$zipSize" -lt 1048576 ]; then
            echo ""
            echo "Error downloading node archive. Please make sure to refresh the node generator page after every run, each script can only be used once."
            cat $nodeZipLocation
            echo ""
            exit 1
    else
            echo "Node downloaded to $nodeZipLocation"
    fi
}

extractNode() {
    echo "Extracting node to $installDir"
    unzip -d $installDir $nodeZipLocation
    echo "Node extracted"
}

setOwners() {
    echo "Setting directory owner to corda (recursive)"
    chown -R "$cordaUser:$cordaUser" ${installDir}
}

setPermissions() {
    echo "Setting permissions of run scripts"
    chmod +x $installDir/run-corda.sh
}

getExternalAddress() {
    echo "Getting public host"
    ip="$(curl -L -X GET "http://onboarder.prod.ws.r3.com/api/session/my-host")"
}

updateExternalAddress(){
    echo "External address is detected to be $ip - is this correct? (y/n)"
    read reply
    if [[ "$reply" = "n" ]]; then
        echo "Enter external address (for example 12.34.56.78 or mycordapp.mydomain.com):"
        read ip
    fi
    echo "External address set to $ip"
}

installSystemdConfiguration() {
    echo "Installing systemd configuration for Corda"
    if type -p systemd && systemctl > /dev/null 2>&1; then
        cp $installDir/corda.service /etc/systemd/system
        systemctl enable corda.service

        if [[ "$includeWebServer" = true ]]; then
            echo "Installing Web Server systemd configuration"
            cp $installDir/corda-webserver.service /etc/systemd/system
            systemctl enable corda-webserver.service
        fi
    else
        echo "System does not support systemd"
    fi
}

runInitialRegistration() {
    echo "Checking if CSR has been started"

    if [[ "$cordaNetwork" == "UAT" ]]; then
        echo "Initiating CSR (because environment is $cordaNetwork), please wait..."
        echo "CSR" > $lockFilename
        cd $installDir
        sudo -u corda java -jar corda.jar --initial-registration --network-root-truststore-password trustpass
    else
        echo "Skipping CSR (because environment is $cordaNetwork)"
    fi
}

startNode() {
    if type -p systemd && systemctl > /dev/null 2>&1; then
        echo "Starting Corda via systemd"
        systemctl start corda
        if [[ "$includeWebServer" = true ]]; then
            systemctl start corda-webserver
        fi
    else
        echo "Starting Corda via run-corda.sh"
        cd $installDir
        sudo -u corda ./run-corda.sh "WEBSERVER=$includeWebServer"
    fi
}

for ARGUMENT in "$@"
do
    KEY=$(echo $ARGUMENT | cut -f1 -d=)
    VALUE=$(echo $ARGUMENT | cut -f2 -d=)
    case "$KEY" in
        O)                        x500O=${VALUE} ;;
        OU)                       x500OU=${VALUE} ;;
        ONE_TIME_DOWNLOAD_KEY)    ONE_TIME_DOWNLOAD_KEY=${VALUE} ;;
        ENV)                      cordaNetwork=${VALUE} ;;
        WEBSERVER)                includeWebServer=${VALUE} ;;
        USEORACLEJDK)             useOracleJdk=${VALUE} ;;
        *)
    esac
done

echo "Starting a node installation for Corda TestNet from environment HOME_NAME"

deleteLockfileIfCertsExist

if [[ ! -f $lockFilename ]]; then
    checkUserIsRoot
    checkEnvironmentVars
    checkJava
    ensurePackageIsInstalled "screen"
    ensurePackageIsInstalled "unzip"
    createUser
    checkDirectory
    getExternalAddress
    updateExternalAddress
    downloadNode
    extractNode
    setOwners
    setPermissions
    installSystemdConfiguration
else
    echo "Jumping to waiting for CSR - $lockFilename exists. To redo the entire setup process, run 'rm $lockFilename' then re-run this script."
fi

runInitialRegistration
startNode

echo "Installation finished successfully."
echo "To restart Corda run: sudo /opt/corda/run-corda.sh WEBSERVER=$includeWebServer"
echo "Note: If you restart your VM you will need to restart Corda"