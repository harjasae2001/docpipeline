#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF)
# Maven Wrapper Script
# ----------------------------------------------------------------------------

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD=`which java`
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

DISTRIBUTION_URL=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d '=' -f2)
MAVEN_HOME="$HOME/.m2/wrapper/dists/$(basename $DISTRIBUTION_URL .zip)"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven..."
  mkdir -p "$MAVEN_HOME"
  curl -sL "$DISTRIBUTION_URL" -o /tmp/maven.zip
  unzip -q /tmp/maven.zip -d "$HOME/.m2/wrapper/dists/"
  rm /tmp/maven.zip
fi

MAVEN_BIN=$(find "$HOME/.m2/wrapper/dists/" -name "mvn" -type f 2>/dev/null | grep "apache-maven" | head -1)

exec "$MAVEN_BIN" "$@"
