#!/usr/bin/env sh
##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

DIR="$( cd "$( dirname "$0" )" && pwd )"
GRADLE_WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
java -jar "$GRADLE_WRAPPER_JAR" "$@"
