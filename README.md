# JRTR Base Code
This project contains base code developed for an introduction to computer graphics course taught by Matthias Zwicker at the University of Maryland, College Park ([CMSC427, Computer Graphics](https://cs.umd.edu/class)), and the University of Bern, Switzerland ([Computergrafik](https://cgg.unibe.ch/teaching/previous-courses)). This is educational code intended for students to learn about 3D graphics programming. The framework provides a skeleton for a real-time rendering engine. It is modular to support different rendering back-ends, coming with an OpenGL-based renderer, and a renderer using OpenGL and OpenVR to render onto VR goggles. 

## Getting Started
Import this project into Eclipse to work with it. First create an empty folder where your project will be located. Start Eclipse and select the created folder as workspace. Now you can import the Java code to the workspace by selecting the menu entry "File->Import->Git->Projects from Git->Next". Then choose "Clone URI". In the next dialogue "Source Git Repository", use https://github.com/mzwicker/JRTR-Base-Code.git as URI. Leave the fields "User" and "Password" empty. In the dialogue "Local Destination", choose the created folder. This folder must be empty. After completion of the import you have to wait some seconds for the initialization of the project creation. If the Maven build is not done correctly by default, you have to update the projects by right-clicking on the project name, then "Maven->Update Project...". Afterwards, you should be able to run the project "simple" and see a rotating cube. If you use an older version of Eclipse, you may have to manually install the plugins for "Maven" and "Git" separately. 
