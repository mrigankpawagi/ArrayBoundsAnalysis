FROM eclipse-temurin:8-jdk

RUN apt-get update && \
    apt-get install -y graphviz img2pdf vim && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash SootAnalysis
USER SootAnalysis
WORKDIR /home/SootAnalysis

COPY --chown=SootAnalysis:SootAnalysis Analysis.java .
COPY --chown=SootAnalysis:SootAnalysis pkgs ./pkgs
COPY --chown=SootAnalysis:SootAnalysis target1-pub ./target1-pub
COPY --chown=SootAnalysis:SootAnalysis README.md .
COPY --chown=SootAnalysis:SootAnalysis build-analysis.sh .
COPY --chown=SootAnalysis:SootAnalysis build-targets.sh .
COPY --chown=SootAnalysis:SootAnalysis environ.sh .
COPY --chown=SootAnalysis:SootAnalysis run-analysis.sh .
COPY --chown=SootAnalysis:SootAnalysis run-analysis-one.sh .
COPY --chown=SootAnalysis:SootAnalysis get-soot.sh .

ENTRYPOINT ["/bin/bash"]
