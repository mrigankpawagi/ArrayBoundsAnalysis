#!/usr/bin/env bash

set -e

## ./run-analysis-one.sh  <Dir>  <MainClass>  <TargetClass>  <TargetMethod>

# <Dir> is the directory that contains the target program.
# <MainClass> is the name of the class that contains the "main" method. 
# <TargetClass> is the class that contains the "target" method
# <TargetMethod> is the target method. You need to analyze this method and
# its callees (i.e., treat this method as if it is your main method).

# For simplicity you can assume that all the classes of your program are in
# a single package, which is the "default" (i.e., unnamed) package.

./get-soot.sh
./build-targets.sh
./build-analysis.sh
./run-analysis-one.sh "./target1-pub" "BasicTest"   "BasicTest"   "fun1" 150
# ./run-analysis-one.sh "./target1-pub" "BasicTest"   "BasicTest"   "fun2" 10

# You should edit the line above whenever you want to change the parameters to the analysis. 
