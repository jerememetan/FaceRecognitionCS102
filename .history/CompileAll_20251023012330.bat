javac -proc:none -Xlint:-options -d compiled -cp ".\src" src\ConfiguRationAndLogging\*.java
<<<<<<< HEAD
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\facecrop\Name_ID_GUI.java src\facecrop\NewFaceRecognitionDemo.java src\facecrop\NewFaceCropDemo.java src\facecrop\MyGUIProgram.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\reportingandexport\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\app\test\EmbeddingQualityTest.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" ".\src\app\Main.java"
echo Compiled Successfully
=======
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\facecrop\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\reportingandexport\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" ".\src\app\Main.java"
javac -d compiled -cp "src;lib/*" src/gui/LoginPage.java src/gui/MainDashboard.java

echo Compiled done
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9
