@echo off
echo ========================================
echo Enhanced Embedding Quality Test Runner
echo ========================================
echo.

cd /d "%~dp0"

echo Compiling test...
call gradlew compileTestJava
if %errorlevel% neq 0 (
    echo ❌ Compilation failed!
    pause
    exit /b 1
)

echo.
echo Running Enhanced Embedding Quality Test...
echo ===========================================
java -cp "build/classes/java/main;build/classes/java/test;lib/*" test.EnhancedEmbeddingQualityTest

echo.
echo Test completed. Press any key to exit...
pause >nul