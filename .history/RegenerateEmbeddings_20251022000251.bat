@echo off
echo ========================================
echo Regenerating ALL Embeddings with Latest Pipeline
echo ========================================
echo.
echo This script uses the latest face recognition pipeline including:
echo - Increased ROI padding (0.25) for better eye detection
echo - Improved face alignment with geometric validation
echo - Relaxed embedding quality thresholds
echo - Enhanced preprocessing pipeline
echo.
echo WARNING: This will overwrite all .emb files!
echo Press Ctrl+C to cancel, or
pause
echo.
echo Deleting old embeddings...
del /Q "data\facedata\*\*.emb" 2>nul
echo.
echo Regenerating embeddings from face images...
echo Using app.test.RegenerateEmbeddings for all student folders...
echo.

for /d %%d in ("data\facedata\*") do (
    echo Processing folder: %%~nxd
    java -cp "compiled;lib/*" app.test.RegenerateEmbeddings "%%d"
    echo.
)

echo.
echo ========================================
echo REGENERATION COMPLETE
echo ========================================
echo All embeddings have been regenerated with the latest pipeline!
echo Run EmbeddingQualityTest to verify the new embeddings.
echo.
pause
