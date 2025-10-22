@echo off
echo ==================================================================================
echo BATCH EMBEDDING REGENERATION - ALL STUDENTS
echo ==================================================================================
echo.
echo This script regenerates embeddings for ALL student folders using the latest
echo face recognition pipeline with all recent improvements:
echo.
echo • Increased ROI padding (0.25) for better eye detection context
echo • Improved face alignment with geometric validation
echo • Separate left/right eye cascades with constrained detection
echo • Heuristic fallback alignment for edge cases
echo • Relaxed embedding quality thresholds (absolute ≥0.6, deviation ≥0.5)
echo • Enhanced preprocessing with minimal denoising
echo.
echo WARNING: This will overwrite ALL .emb files in ALL student folders!
echo Press Ctrl+C to cancel, or
pause
echo.
echo Starting batch regeneration...
java -cp "compiled;lib/*" app.test.BatchRegenerateEmbeddings
echo.
echo ==================================================================================
echo BATCH REGENERATION COMPLETE
echo ==================================================================================
echo.
echo Next steps:
echo 1. Run RunEmbeddingTest.bat to verify embedding quality
echo 2. Restart the face recognition application
echo.
pause