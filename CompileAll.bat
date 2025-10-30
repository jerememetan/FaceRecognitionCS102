javac -proc:none -Xlint:-options -d compiled -cp ".\src" src\ConfigurationAndLogging\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\reportingandexport\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\app\entity\*.java src\app\gui\*.java src\app\model\*.java src\app\repository\*.java src\app\service\*.java src\app\test\*.java src\app\util\*.java
javac -proc:none -Xlint:-options -d compiled -cp "src;lib/*" src/gui/LoginPage.java src/gui/MainDashboard.java

