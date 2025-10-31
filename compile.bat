javac -proc:none -Xlint:-options -d compiled -cp ".\src" src\config\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\report\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\src;.\lib\*" src\entity\*.java src\model\*.java src\repository\*.java src\service\embedding\*.java src\service\recognition\*.java src\service\session\*.java src\service\student\*.java src\util\*.java src\gui\config\*.java src\gui\detection\*.java src\gui\homepage\*.java src\gui\recognition\*.java src\gui\settings\*.java
javac -proc:none -Xlint:-options -d compiled -cp "src;lib/*" src/gui/homepage/LoginPage.java src/gui/homepage/MainDashboard.java

