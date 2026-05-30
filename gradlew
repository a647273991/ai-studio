#!/bin/sh
# Gradle Wrapper startup script
# Standard gradle wrapper - downloads gradle distribution if needed

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Determine JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Get the app's home dir
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Check if wrapper jar exists, if not download gradle directly
GRADLE_VERSION=8.11.1
if [ ! -f "$CLASSPATH" ]; then
    GRADLE_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}"
    if [ ! -d "$GRADLE_DIR" ]; then
        echo "Downloading Gradle $GRADLE_VERSION..."
        mkdir -p "$GRADLE_DIR"
        ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
        curl -sL "$ZIP_URL" -o "/tmp/gradle-$GRADLE_VERSION.zip" --connect-timeout 30 --max-time 300
        unzip -q -o "/tmp/gradle-$GRADLE_VERSION.zip" -d "$GRADLE_DIR"
        rm -f "/tmp/gradle-$GRADLE_VERSION.zip"
    fi
    GRADLE_BIN=$(find "$GRADLE_DIR" -name "gradle" -path "*/bin/gradle" | head -1)
    exec "$GRADLE_BIN" "$@"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
