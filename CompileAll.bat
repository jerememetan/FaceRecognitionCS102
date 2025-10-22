javac -proc:none -Xlint:-options -d compiled -cp ".\src" src\ConfiguRationAndLogging\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\facecrop\Name_ID_GUI.java src\facecrop\NewFaceRecognitionDemo.java src\facecrop\NewFaceCropDemo.java src\facecrop\MyGUIProgram.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\reportingandexport\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" ".\src\app\Main.java"
echo Compiled Successfully