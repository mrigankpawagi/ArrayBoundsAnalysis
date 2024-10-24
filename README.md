## Steps  to build the tool and the target programs, and to run the tool:
- Build Docker image
  ```
  docker build -t pav_project:v1 .
  ```

- Then, to build or use the tool, run:
  ```
  docker run --name pav_project -it pav_project:v1
  ```

  If you exit the running container, you can re-start it by noting the name of the container (pav_project), and then running
  ```
  docker start -i pav_project
  ```

  Within the interactive shell of the container, cd to /home/SootAnalysis, then build the target program and the tool, and run the tool on the target program, as explained below:

- To compile the target programs (i.e., the test programs to analyze),  run from the shell command line:
  ```
  ./build-targets
  ```

- To compile the Analysis:
  ```
  ./build-analysis
  ```

- To run the analysis on the test program:
  ```
  ./run-analysis.sh
  ```

## Authors
- Alan Jojo
- Mrigank Pawagi

## Project status
Under construction and testing