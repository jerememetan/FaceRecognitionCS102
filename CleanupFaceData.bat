@echo off
echo ========================================
echo Face Data Cleanup Script
echo ========================================
echo.
echo This will DELETE all existing face data and embeddings.
echo You will need to RECAPTURE all faces after running this.
echo.
echo Reason: Old embeddings were generated from degraded grayscale images.
echo         New embeddings will use color images for better accuracy.
echo.
pause

echo.
echo Deleting face data folders...
echo.

if exist "data\facedata\S12312_renard" (
    echo Deleting S12312_renard...
    rmdir /s /q "data\facedata\S12312_renard"
    echo   Deleted.
) else (
    echo   S12312_renard does not exist, skipping.
)

if exist "data\facedata\S13234_Jin_Rae" (
    echo Deleting S13234_Jin_Rae...
    rmdir /s /q "data\facedata\S13234_Jin_Rae"
    echo   Deleted.
) else (
    echo   S13234_Jin_Rae does not exist, skipping.
)

if exist "data\facedata\S32425_jose" (
    echo Deleting S32425_jose...
    rmdir /s /q "data\facedata\S32425_jose"
    echo   Deleted.
) else (
    echo   S32425_jose does not exist, skipping.
)

echo.
echo ========================================
echo Cleanup Complete!
echo ========================================
echo.
echo Next steps:
echo 1. Compile the updated code:
echo    .\CompileConfigurationAndLogging.bat
echo    .\compileStudentManager.bat
echo.
echo 2. Recapture faces for all three people using the GUI
echo.
echo 3. Run face recognition - it should be MUCH more accurate!
echo.
pause
