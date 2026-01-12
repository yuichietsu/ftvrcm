#!/usr/bin/env sh

# Minimal Gradle wrapper script

set -eu

APP_HOME=$(cd "${0%/*}" && pwd -P)

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHARED_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper-shared.jar"
WRAPPER_CLI_JAR="$APP_HOME/gradle/wrapper/gradle-cli.jar"

if [ ! -f "$WRAPPER_JAR" ] || [ ! -f "$WRAPPER_SHARED_JAR" ] || [ ! -f "$WRAPPER_CLI_JAR" ]; then
  echo "Missing $WRAPPER_JAR" >&2
  echo "Missing $WRAPPER_SHARED_JAR" >&2
  echo "Missing $WRAPPER_CLI_JAR" >&2
  echo "Run: make-wrapper (or re-run setup)" >&2
  exit 1
fi

JAVA_CMD=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
JAVA_CMD=${JAVA_CMD:-java}

# Android SDK: prefer ANDROID_SDK_ROOT, fall back to ~/Android/Sdk.
# AGP still commonly checks ANDROID_HOME or local.properties (sdk.dir).
if [ -z "${ANDROID_HOME:-}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi
if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -d "$HOME/Android/Sdk" ]; then
  export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Android/Sdk" ]; then
  export ANDROID_HOME="$HOME/Android/Sdk"
fi

exec "$JAVA_CMD" -classpath "$WRAPPER_JAR:$WRAPPER_SHARED_JAR:$WRAPPER_CLI_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
