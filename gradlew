#!/bin/sh
# Gradle wrapper minimal — mendownload gradle jika belum ada
DIR=$(cd "$(dirname "$0")" && pwd)
GRADLE_VERSION=8.6
GRADLE_HOME="$DIR/.gradle-dist/gradle-$GRADLE_VERSION"
if [ ! -d "$GRADLE_HOME" ]; then
  echo "Downloading Gradle $GRADLE_VERSION..."
  mkdir -p "$DIR/.gradle-dist"
  cd "$DIR/.gradle-dist"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    wget -O gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  fi
  unzip -q gradle.zip
  rm gradle.zip
  cd "$DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
