#!/usr/bin/env bash

set -e

source environ.sh
echo === building Analysis.java

javac -g pav/Pair.java
javac -g pav/LatticeElement.java
javac -g pav/IntervalElement.java
javac -g pav/IntegerArrayPointer.java
javac -g pav/Printer.java
javac -g Analysis.java
