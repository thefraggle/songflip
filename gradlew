#!/usr/bin/env sh

# Resolving links from the command line
PRG="$0"
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done
SAVED="`pwd`"
CDPATH=""
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use Microsoft OpenJDK 17 if JAVA_HOME not set
if [ -z "$JAVA_HOME" ] && [ -d "/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home"
fi

if [ -z "$ANDROID_HOME" ] && [ -d "/Users/daniel.notthoff/Library/Android/sdk" ]; then
    export ANDROID_HOME="/Users/daniel.notthoff/Library/Android/sdk"
fi

# Find java
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses Strange Paths
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Locate gradle-wrapper.jar
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Download gradle-wrapper.jar if missing
if [ ! -e "$CLASSPATH" ]; then
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -s -L -o "$CLASSPATH" https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar || true
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
