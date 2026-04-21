#!/bin/sh
# Maven Wrapper Script
# Allows building without a globally installed Maven.
# Usage: ./mvnw [goals]

set -e

MAVEN_VERSION=3.9.6
MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven ${MAVEN_VERSION}..."
  mkdir -p "$MAVEN_HOME"
  curl -fsSL "$MAVEN_URL" | tar -xz --strip-components=1 -C "$MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
