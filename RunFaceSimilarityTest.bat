@echo off
REM Run Face Similarity Test
REM Tests intra-person vs inter-person face similarities

echo ========================================
echo   FACE SIMILARITY TEST - DIAGNOSTIC
echo ========================================
echo.
echo This test analyzes the quality of stored face embeddings.
echo It checks for corruption and discrimination capability.
echo.
echo IMPORTANT: Current embeddings appear to be CORRUPTED!
echo This is likely due to the grayscale preprocessing bug.
echo.
echo SOLUTION: Regenerate all face data using fixed code
echo 1. Run CleanupFaceData.bat to delete corrupted embeddings
echo 2. Recapture all faces using FaceDetection.java (fixed)
echo 3. Run this test again to verify improvements
echo.
echo ========================================

REM Set the classpath to match existing test scripts
set CLASSPATH=compiled;lib/*

REM Run the test
java -cp "%CLASSPATH%" app.test.FaceSimilarityTest

echo.
echo ========================================
echo   TEST COMPLETE
echo ========================================
echo.
echo If embeddings are still corrupted, the issue is:
echo - Old embeddings not deleted before recapture
echo - Fixed code not deployed properly
echo - OpenCV native libraries not loading correctly
echo.
pause