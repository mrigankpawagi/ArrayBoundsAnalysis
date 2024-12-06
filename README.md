## Steps  to build the tool and the target programs, and to run the tool:
- Build Docker image
  ```bash
  docker build -t pav_project:v1 .
  ```

- Then, to build or use the tool, run:
  ```bash
  docker run --name pav_project -it pav_project:v1
  ```

  If you exit the running container, you can re-start it by noting the name of the container (pav_project), and then running
  ```bash
  docker start -i pav_project
  ```

  Within the interactive shell of the container, cd to /home/SootAnalysis, then build the target program and the tool, and run the tool on the target program, as explained below:

- To compile the Analysis:
  ```bash
  ./get-soot.sh # Downloads the Soot package
  ./setup.sh # set up the environment
  ./build-analysis
  ```

- To run the analysis on a target program, use the following command.
  ```bash
  ./run-analysis.sh <Dir> <MainClass> <TargetClass> <TargetMethod>
  ```

## Authors
- [Alan Jojo](https://github.com/AlanJojo)
- [Mrigank Pawagi](https://github.com/mrigankpawagi)
