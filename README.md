# JRTR Base Code
This project contains base code developed for an introduction to computer graphics course taught by Matthias Zwicker at the University of Maryland, College Park ([CMSC427, Computer Graphics](https://cs.umd.edu/class)). This is educational code intended for students to learn about 3D graphics programming. The framework provides a skeleton for a real-time rendering engine. 

## Getting Started
Import this project into Eclipse to work with it. First create an empty folder where your project will be located. Start Eclipse and select the created folder as workspace. Now you can import the Java code to the workspace by selecting the menu entry "File->Import->Git->Projects from Git->Next". Then choose "Clone URI". In the next dialogue "Source Git Repository", use https://github.com/mzwicker/JRTR-Base-Code-2021.git as URI. Leave the fields "User" and "Password" empty. In the dialogue "Local Destination", choose the created folder. This folder must be empty. After completion of the import, wait a few seconds for the initialization of the project creation. If the Maven build is not done correctly by default and you see errors in the code, update the projects by right-clicking on the project name, then "Maven->Update Project...". Afterwards, you should be able to run the project "simple" and see a rotating cube. If you use an older version of Eclipse, you may have to manually install the plugins for "Maven" and "Git" separately. 

## Known issues
This project should compile on Windows, Linux, and macOS, with the exception M1 based macOS systems. The stable release of LWJGL doesn't currently support M1 architecture, and I haven't yet been successful running this project on the early access release version.

### Unsupported GLSL version
If you encounter a compilation error similar to the one below, you can modify the shaders under `jrtr/shaders` and specify a version supported by your system.
```
0:1(10): error: GLSL 1.50 is not supported. Supported versions are: 1.10, 1.20, 1.30, 1.40, 1.00 ES, and 3.00 ES
```
Note: In this example, substituting `#version 150` with `#version 140` should resolve the compilation failure.

### Maven dependencies
Sometimes downloading the dependencies using Maven doesn't work correctly, leading to errors in the code after following the steps in "Getting Started". In this case, try removing the files in  ~\.m2\repository\org\lwjgl (where ~ is the user's home directory) and going through the steps for updating the Maven build described under "Getting Started" again. This should solve the problem.

## Working on Assignments
Public forks of this repository that contain solutions of any course assignments are prohibited. We recommend that you store your work in your own private repository on [GitHub](https://github.com/) or a similar platform. To work with GitHub, see the [documentation here](https://docs.github.com/en).
