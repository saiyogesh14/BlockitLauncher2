#!/bin/sh
# Gradle wrapper script

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Default JVM opts
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

set -e
exec java $DEFAULT_JVM_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
