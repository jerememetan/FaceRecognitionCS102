--------------------------------------------------------------------------
Nicholas's Edits to make this project accessible independant on the system file location of opencv-480.jar and opencv_java480.dll
--------------------------------------------------------------------------

-------------------------
Using VS Code Run Button
-------------------------
Nicholas's Edits to make openCV .jar and .dll accessible from lib/* for any local machine

1) In .vscode/settings.json, in java.project.referencedLibraries, we call .jar no issue
2) In both FaceCropDemo.java and FaceRecognitionDemo.java, we do:

    System.load(new File("lib/opencv_java480.dll").getAbsolutePath());

    Which is an indirect direct way to get the absolute path of the file. 

-------------------------
Using Command Line Terminal (powershell) [Untested]
-------------------------
COMMANDS:

    javac -cp ".;./lib/opencv-480.jar" FaceRecognitionDemo.java
    java --enable-native-access=ALL-UNNAMED "-Djava.library.path=./lib" -cp ".;./lib/opencv-480.jar" FaceRecognitionDemo

// Above code does not work

// For CLI, we have to run JAVAC and JAVA
// TO BE FIXED and TESTED:  replace [System.Load] with [System.LoadLibrary] in .java folder which checks Djava.library.path
// COMMAND [--enable-native-access=ALL-UNNAMED] is needed to allow access to [System.wtvLibrary]
// COMMAND [-Djava.library.path=./lib] means -D java.library.path, which .dll needs to be in for .java files to read using [System.LoadLibrary]
// COMMAND [.;./lib/opencv-480.jar] is the classpath of for [opencv-480.jar], this command works

