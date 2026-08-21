#!/usr/bin/env sh
set -eu
APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  "$APP_HOME/scripts/bootstrap-gradle-wrapper.sh"
fi
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="java"
fi
exec "$JAVA" -Xmx128m -Xms64m -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
