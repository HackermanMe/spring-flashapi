#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper script for Unix/Linux/macOS
# Downloads and caches Apache Maven if not already present.
# ----------------------------------------------------------------------------

set -e

MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
PROPERTIES_FILE="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
WRAPPER_CACHE_DIR="$MAVEN_USER_HOME/wrapper/dists"

# Read distributionUrl from properties
if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "ERROR: Could not find $PROPERTIES_FILE" >&2
    exit 1
fi

distributionUrl=$(grep "^distributionUrl=" "$PROPERTIES_FILE" | cut -d'=' -f2-)

if [ -z "$distributionUrl" ]; then
    echo "ERROR: distributionUrl not found in $PROPERTIES_FILE" >&2
    exit 1
fi

# Derive Maven version and cache path
MAVEN_ZIP_NAME=$(basename "$distributionUrl")
MAVEN_DIR_NAME="${MAVEN_ZIP_NAME%-bin.zip}"
MAVEN_HOME_DIR="$WRAPPER_CACHE_DIR/$MAVEN_DIR_NAME"

# Download and extract if not cached
if [ ! -d "$MAVEN_HOME_DIR" ]; then
    echo "Downloading Maven from $distributionUrl..."
    mkdir -p "$WRAPPER_CACHE_DIR"
    TMP_FILE="$WRAPPER_CACHE_DIR/$MAVEN_ZIP_NAME"

    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "$TMP_FILE" "$distributionUrl"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$TMP_FILE" "$distributionUrl"
    else
        echo "ERROR: curl or wget required to download Maven." >&2
        exit 1
    fi

    echo "Extracting Maven..."
    unzip -q -d "$WRAPPER_CACHE_DIR" "$TMP_FILE"
    rm -f "$TMP_FILE"

    if [ ! -d "$MAVEN_HOME_DIR" ]; then
        echo "ERROR: Expected directory $MAVEN_HOME_DIR not found after extraction." >&2
        exit 1
    fi
fi

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="$(command -v java)" || {
        echo "ERROR: JAVA_HOME not set and 'java' not in PATH." >&2
        exit 1
    }
fi

# Run Maven
exec "$MAVEN_HOME_DIR/bin/mvn" \
    -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
    $MAVEN_OPTS \
    "$@"
