FROM eclipse-temurin:8-jdk

RUN apt-get update && \
    apt-get install -y graphviz img2pdf vim && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /home/ubuntu

COPY . .

ENTRYPOINT ["/bin/bash"]
