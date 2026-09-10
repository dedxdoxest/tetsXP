#!/bin/sh

##############################################################################
# Gradle start up script for POSIX
##############################################################################

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_VERSION="8.4"

CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Increase the maximum file descriptors if we can.
MAX_FD="maximum"
MAX_FD_LIMIT=`ulimit -H -n`
if [ $? -eq 0 ] ; then
    if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
        MAX_FD="$MAX_FD_LIMIT"
    fi
    ulimit -n $MAX_FD
fi

# Collect all arguments for the java command, stacking in reverse order:
set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

exec "$JAVACMD" "$@"
