--------------------------------------------------------------------------
Current UML Class Diagram Last Edit -> Nicholas 25/9/2025:
--------------------------------------------------------------------------

                [  My GUI Program  ]
                    /           \
    [  FaceCropDemo  ]      [  FaceRecognition  ]
        /
[  Name_ID_GUI  ]


--------------------------------------------------------------------------
Make openCV .jar and .dll accessible from lib/* for any local machine
--------------------------------------------------------------------------

1) In .vscode/settings.json, in java.project.referencedLibraries, we call .jar no issue
2) In both all java files, we do:

    System.load(new File("lib/opencv_java480.dll").getAbsolutePath());

    Which is an indirect direct way to get the absolute path of the file. 
