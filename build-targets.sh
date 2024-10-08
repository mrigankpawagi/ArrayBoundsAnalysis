#!/usr/bin/env bash

set -e

#export CLASSPATH=.:pkgs/soot-4.3.0-with-deps.jar
source environ.sh
echo === building targets

cd target1-pub;   javac -g *.java;  cd ..





