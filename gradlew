#!/bin/sh

##############################################################################
##  Gradle wrapper script for Linux/Mac
##############################################################################

# Attempt to set APP_HOME
APP_HOME="$(dirname "$0")"

# Add default JVM options here
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Resolve $JAVA_HOME
if [ -z "$JAVA_HOME" ] ; then
    JAVACMD=java
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

# Download gradle wrapper jar if missing
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$APP_HOME/gradle/wrapper"
    echo "Downloading gradle-wrapper.jar..."
    curl -sL -o "$WRAPPER_JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$WRAPPER_JAR" "$@"
