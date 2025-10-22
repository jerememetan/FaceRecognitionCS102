@echo off
echo === Embedding Similarity Test ===
echo.

REM Compile the test
echo Compiling SimpleEmbeddingSimilarityTest.java...
javac -proc:none -Xlint:-options -d "compiled" -cp "./src;./lib/*" src/test/SimpleEmbeddingSimilarityTest.java
if %errorlevel% neq 0 (
    echo ❌ Compilation failed!
    pause
    exit /b 1
)
echo ✅ Compilation successful!
echo.

REM Run the test
echo Running embedding similarity analysis...
java -cp "./compiled;./src;./lib/*" test.SimpleEmbeddingSimilarityTest
if %errorlevel% neq 0 (
    echo ❌ Test execution failed!
    pause
    exit /b 1
)

echo.
echo ✅ Test completed successfully!
pause