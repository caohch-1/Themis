# Themis: RPC-Driven Race Detection and Directed Fuzz Validation for Distributed Systems


## 1. Contents and Goals

This repository implements a prototype of Themis, a framework for detecting distributed concurrency bugs that arise when concurrent execution flows access shared variables or objects in conflicting ways under certain interleavings. 
The goal is to implement the paper’s three-stage approach that integrates static RPC-driven race detection, LLM-based test generation, and directed fuzzing to detect and validate distributed concurrency bugs.
The repository is organized as a layered implementation of the paper's three-part methodology, where each module occupies a distinct analytical role while exchanging strongly typed intermediate representations.
- **Static-analysis module**: operationalizes **Static RPC-Driven Race Detection** by extracting RPC-reachable variable accesses, applying violation rules, and producing candidate harmful concurrency violations.
- **LLM-generation module**: implements **LLM-Based Holistic Test Generation** by transforming static candidates into executable harnesses through generation-and-repair cycles and parameter-seed preparation.
- **Fuzz-validation module**: implements **Staged Parameter Fuzzing and Interleaving Exploration** by conducting race-targeted fuzzing, interleaving manipulation, and symptom-targeted follow-up validation.
- **Orchestration module**: acts as the pipeline governor, coordinating stage ordering, argument propagation, and emission for either staged or end-to-end execution.
- **Configuration and dependency substrate**: supplies prompt templates, policy parameters, and scripts for easier experiment and usage.


## 2. Requirements

### Hardware

The only hardware requirement is memory capacity: at least 128 GB RAM is required to stably load and analyze the bytecode of multiple large target systems. In addition, fuzzing throughput and effectiveness are influenced by CPU parallelism and clock speed, cache and memory bandwidth, storage I/O latency and stability, etc.


### Software

- **OS**: Tested on Ubuntu 20.04.
- **Java**: JDK 8 for Soot and target system compatibility; a quick sanity probe is `java -version` plus `javac -version`.
- **Soot**: Version 3.0.0 as the static program analysis core.
- **JQF**: Used as the Java fuzzing backend; a practical launcher check is `jqf-afl-fuzz` after build bootstrap.
- **SelectFuzz**: Used as the directed fuzzing algorithm backend; a minimal runtime check is `afl-fuzz -h`.


## 3. Instructions

### Dependency installation

Stage I should be treated as a coupled bootstrap of two independently evolved fuzzing substrates, namely JQF (coverage-guided JVM fuzzing) and SelectFuzz (selective directed mutation infrastructure), followed by an integration sanity pass that confirms they can coexist under Themis orchestration.

1. **Java and Soot Installation**: Java 8 can be downloaded and installed from https://www.java.com/en/download/manual.jsp or https://openjdk.org/projects/jdk8/. Soot is included in the Maven dependency configuration, but you may still substitute a local Soot artifact by changing settings accordingly. Note that higher Java or Soot versions may introduce incompatibility exceptions. After installation, validate toolchain integrity with `java -version`, `javac -version`, and `mvn -version`.

2. **JQF substrate construction**: Bootstrap the JQF component and execute a Maven package cycle that materializes launchers plus instrumentation and fuzzing artifacts, typically through `mvn -q -DskipTests package`. In practical terms, verify that the launcher is executable by running `jqf-afl-fuzz`; otherwise, Themis cannot execute its JQF-backed stage runners.

3. **SelectFuzz substrate construction**: Set the expected environment context with `export AFLGO="$PWD"`, perform a clean top-level build via `make clean all`, and repeat an equivalent clean build in LLVM mode. The SelectFuzz documentation notes that an intermediate `test_build` warning in LLVM mode may be non-fatal; the practical success criterion is that the core fuzzing binary is callable (`afl-fuzz -h`).

### Framework Execution

Once the dependency stratum has been stabilized, running Themis becomes an exercise in orchestrating a multi-phase analytical continuum: compile the static-analysis and fuzz-validation subsystems through a Maven reactor pass (`mvn -q -DskipTests package`), optionally run verification (`mvn -q test`), and then invoke either staged commands such as `java -cp "<assembled-classpath>" themis static-detect --config <config> --out <static-output>` and `java -cp "<assembled-classpath>" themis fuzz-validate --config <config> --out <fuzz-output>`, or the integrated pipeline invocation `java -cp "<assembled-classpath>" themis pipeline --config <config> --out <run-output>`.

If LLM-mediated test generation is desired, expose a valid Claude credential through the expected environment variable before execution (`export ANTHROPIC_API_KEY="<claude-api-key>"`); absent this credential, the generative harness-synthesis phase will be unavailable.

You can check the result for a single system by specifying the arguments of the compiled jar file as follows:
```
# Legal system name includes mapreduce, yarn, hdfs, hadoop, hbase, tez, hive, and alluxio
java -jar ./target/Themis-1.0-SNAPSHOT.jar [system_name]

# failed-timeout is the given budget for triggering a violation.
# The suggested value is 1800, you can set it to a lower value to get the results quicker.
# However, it should not lower than 600, which would makes some violations failed to trigger.
java -jar ./target/ThemisFuzzer-1.0-SNAPSHOT-all.jar [system_name] --failed-timeout 600
```



## 4. Citation
If you use Themis in your research, please cite the paper as follows.
```
@inproceedings{cao2026themis,
title={Themis: Detecting Distributed Concurrency Bugs through RPC-Driven Race-Directed Test Generation and Fuzzing},
author={Hongchen Cao and Jingzhu He and Ting Dai and Guoliang Jin},
booktitle={Proceedings of the 23rd USENIX Symposium on Networked Systems Design and Implementation (NSDI'26)},
month=MAY,
year=2026
}
```
