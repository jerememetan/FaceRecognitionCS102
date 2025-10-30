javac -proc:none -Xlint:-options -d compiled -cp ".\srb" srb\config\*.java
javac -proc:none -Xlint:-options -d compiled -cp ".\srb;.\lib\*" srb\report\*.java
javac -proc:none -Xlint:-options -d "compiled" -cp ".\srb;.\lib\*" srb\entity\*.java srb\model\*.java srb\repository\*.java srb\service\embedding\*.java srb\service\recognition\*.java srb\service\session\*.java srb\service\student\*.java srb\util\*.java srb\gui\config\*.java srb\gui\detection\*.java srb\gui\homepage\*.java srb\gui\recognition\*.java srb\gui\settings\*.java
javac -proc:none -Xlint:-options -d compiled -cp "srb;lib/*" srb/gui/homepage/LoginPage.java srb/gui/homepage/MainDashboard.java

