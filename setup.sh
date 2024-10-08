#!/usr/bin/env bash

set -e

echo 'public class detectOs{ public static void main (String[] args) { String os = System.getProperty("os.name").toLowerCase();  System.out.println(os);}}' > detectOs.java
javac detectOs.java
JAVA_OS=`java detectOs`
rm -f detectOs.*

JAVA_OS=${JAVA_OS%% *}
if [ "$JAVA_OS" == "windows" ]; then
    echo 'export CLASSPATH=".;pkgs/soot-4.3.0-with-deps.jar"'  > environ.sh
else
    echo 'export CLASSPATH=".:pkgs/soot-4.3.0-with-deps.jar"'  > environ.sh
fi

