#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
JDK_VER=21.0.2_13
JDK_DIR="$ROOT/.jdk"
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_linux_hotspot_${JDK_VER}.tar.gz"
MAVEN_VER=3.9.6
MVN_DIR="$ROOT/.maven"
MVN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VER}/binaries/apache-maven-${MAVEN_VER}-bin.tar.gz"

mkdir -p "$JDK_DIR" "$MVN_DIR"
if [ ! -d "$JDK_DIR/jdk-17" ]; then
  echo "Downloading JDK..."
  curl -fsSL "$JDK_URL" | tar -xz -C "$JDK_DIR"
  mv "$JDK_DIR"/jdk-17* "$JDK_DIR/jdk-17"
fi
if [ ! -d "$MVN_DIR/maven" ]; then
  echo "Downloading Maven..."
  curl -fsSL "$MVN_URL" | tar -xz -C "$MVN_DIR"
  mv "$MVN_DIR"/apache-maven-* "$MVN_DIR/maven"
fi
export JAVA_HOME="$JDK_DIR/jdk-17"
export PATH="$JAVA_HOME/bin:$MVN_DIR/maven/bin:$PATH"
java -version
mvn -v
mvn -q -DskipTests package
