#!/bin/sh
MAVEN_VERSION=3.9.8
MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
if [ ! -d "${MAVEN_HOME}/bin" ]; then
  echo "Downloading Maven ${MAVEN_VERSION}..."
  mkdir -p "${MAVEN_HOME}"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar xz --strip-components=1 -C "${MAVEN_HOME}"
fi
exec "${MAVEN_HOME}/bin/mvn" "$@"
