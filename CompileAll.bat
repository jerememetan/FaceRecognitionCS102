javac -proc:none -Xlint:-options -d compiled -cp ".\src" src\ConfiguRationAndLogging\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\facecrop\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\reportingandexport\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" ".\src\app\Main.java"
javac -d compiled -cp "src;lib/*" src/gui/LoginPage.java src/gui/MainDashboard.java
<<<<<<< HEAD

echo Compiled done
=======
javac -d compiled -cp "src;lib/*" src/test/EnhancedEmbeddingQualityTest.java
javac -d compiled -cp "src;lib/*" src/test/DebugEmbeddingIssues.java

echo Compiled done
>>>>>>> origin/JR-StudentManager
