#!/usr/bin/env bash

set -e

source environ.sh
echo === building targets

cd target1-pub
javac -g *.java
cd ..

cd phase2-pub
javac -g *.java
cd ..
