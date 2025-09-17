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
Using Command Line Terminal (powershell)
-------------------------

