@echo off
echo ========================================
echo Regenerating ALL Embeddings with CLAHE=1.0
echo ========================================
echo.
echo WARNING: This will overwrite all .emb files!
echo Press Ctrl+C to cancel, or
pause
echo.
echo Deleting old embeddings...
del /Q "data\facedata\S13234_Jin_Rae\*.emb" 2>nul
del /Q "data\facedata\S12342_wyn\*.emb" 2>nul
echo.
echo Regenerating embeddings from face images...
java -cp "compiled;lib/*" facecrop.ReprocessEmbeddings
echo.
echo Done! Please restart the recognition application.
pause
