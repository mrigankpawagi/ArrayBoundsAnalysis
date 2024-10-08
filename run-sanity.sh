#!/usr/bin/env bash

rm -rf sanity/
mkdir -p sanity/

cp target1-pub/BasicTest1.class  sanity/

./run-analysis-one.sh "./target1-pub" "BasicTest1"   "BasicTest1"   "myIncrement"

