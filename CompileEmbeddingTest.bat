@echo off
javac -proc:none -Xlint:-options -d compiled -cp ".\src;.\lib\*" src\app\test\EmbeddingQualityTest.java
