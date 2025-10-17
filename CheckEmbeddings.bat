@echo off
echo ================================================
echo  Embedding Consistency Checker
echo ================================================
echo.
echo This tool checks if your embeddings were generated
echo correctly (from raw images, not preprocessed ones).
echo.
echo This will SCAN only. Use --fix to regenerate.
echo.
pause

call gradlew build -x test
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Running consistency check...
echo.
java -cp "build/classes/java/main;lib/*" app.util.EmbeddingConsistencyChecker

echo.
echo ================================================
echo  To fix inconsistent embeddings, run:
echo  .\CheckEmbeddings.bat --fix
echo ================================================
pause
