#!/usr/bin/env bash

set -e

mkdir -p pkgs/
curl -L https://repo1.maven.org/maven2/org/soot-oss/soot/4.3.0/soot-4.3.0-jar-with-dependencies.jar -o pkgs/soot-4.3.0-with-deps.jar

