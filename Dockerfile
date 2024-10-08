FROM eclipse-temurin:8-jdk

WORKDIR /home/SootAnalysis
COPY Analysis.java .
COPY pkgs ./pkgs
COPY target1-pub ./target1-pub
COPY README.org .
COPY build-analysis.sh .
COPY build-targets.sh .
COPY environ.sh .
COPY run-analysis.sh .
COPY run-analysis-one.sh .
COPY get-soot.sh .

RUN apt-get update
RUN apt-get -y install graphviz
RUN apt install vim -y

ENTRYPOINT ["/bin/bash"]
