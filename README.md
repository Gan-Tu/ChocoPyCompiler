# ChocoPyCompiler

[![Build Status](https://travis-ci.com/Michael-Tu/ChocoPyCompiler.svg?token=mGNinjpZsndTxTSax5FR&branch=master)](https://travis-ci.com/Michael-Tu/ChocoPyCompiler)

This project is done as a part of UC Berkeley undergraduate compilers course: [CS164 Spring 2019](http://inst.eecs.berkeley.edu/~cs164/sp19/).

It includes a fully functional ChocoPy compiler for
- lexing and parsing of ChocoPy programs into an abstract syntax tree (AST)
- semantic analysis and type checking of the program
- RISC-V assembly code generation

## ChocoPy Programming Language

[ChocoPy](https://chocopy.org) is a restricted subset of Python 3, which can easily be compiled to a target such as RISC-V assembly code. The language is fully specified using formal grammar, typing rules, and operational semantics. It was designed by [Rohan Padhye](https://people.eecs.berkeley.edu/~rohanpadhye/) and [Koushik Sen](https://people.eecs.berkeley.edu/~ksen/?rnd=1556232605921), and recently updated by [Paul Hilfinger](https://www2.eecs.berkeley.edu/Faculty/Homepages/hilfinger.html).

You can learn more in [ChocoPy language reference manual](chocopy_language_reference.pdf), or [ChocoPy implementation guide](chocopy_implementation_guid.pdf).

## Build

To build the project, run

```
mvn clean package
```

This will compile and package the code into a **standalone, executable** JAR file at `target/compiler.jar`.

## Using the Compiler

To run a ChocoPy (`.py`) program, run

```
java -jar compiler.jar --execute /path/to/your_program.py
```

_Instructions on more optional commands will be updated in README soon_

## Software Dependencies

The software required for this assignment is as follows:

* Git, version 2.5 or newer: [https://git-scm.com/downloads](https://git-scm.com/downloads)
* Java Development Kit (JDK), version 8 or newer: [http://www.oracle.com/technetwork/](http://www.oracle.com/technetwork/), [java/javase/downloads/index.html](java/javase/downloads/index.html)
* Apache Maven, version 3.3.9 or newer: [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
* (optional) An IDE such as IntelliJ IDEA (free community editor or ultimate edition for students):
[https://www.jetbrains.com/idea](https://www.jetbrains.com/idea).
* (optional) Python, version 3.6 or newer, for running ChocoPy programs in a Python interpreter:
  [https://www.python.org/downloads](https://www.python.org/downloads)

If you are using Linux or MacOS, we recommend using a package manager such as `apt` or `homebrew`. Otherwise, you can simply download and install the software from the websites listed above. We also recommend using an IDE to develop and debug your code. In IntelliJ, you should be able to import the repository as a Maven project.

