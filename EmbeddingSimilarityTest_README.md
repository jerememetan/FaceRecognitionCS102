# Embedding Similarity Test

This script analyzes the quality of face embeddings stored in the `data/facedata/` directory.

## What it does:

1. **Loads all embeddings** from all person directories in `data/facedata/`
2. **Validates embeddings** using the same validation logic as the recognition system
3. **Calculates intra-person similarities** (similarity between different images of the same person)
4. **Calculates inter-person similarities** (similarity between images of different persons)
5. **Generates statistics** and assessment of embedding quality
6. **Prints a similarity matrix** showing average similarities between all person pairs

## How to run:

Double-click `RunEmbeddingSimilarityTest.bat` or run it from command line:

```batch
RunEmbeddingSimilarityTest.bat
```

## Output interpretation:

### Statistics Section:
- **Intra-person similarities**: How similar different images of the same person are
  - Higher values (closer to 1.0) are better
  - Should typically be > 0.7 for good embeddings

- **Inter-person similarities**: How similar images of different persons are
  - Lower values (closer to 0.0) are better
  - Should typically be < 0.5 for good separation

- **Separation margin**: Difference between average intra and inter similarities
  - > 0.2: Good separation ✅
  - 0.1 - 0.2: Moderate separation ⚠️
  - < 0.1: Poor separation ❌

### Similarity Matrix:
- Diagonal shows intra-person similarity (should be high)
- Off-diagonal shows inter-person similarity (should be low)
- Values closer to 1.0 indicate high similarity
- Values closer to 0.0 indicate low similarity

## Troubleshooting:

- If no embeddings are found, check that `data/facedata/` contains subdirectories with `.emb` files
- If embeddings are invalid, they may have been corrupted during generation
- Make sure OpenCV and other dependencies are properly loaded

## Example Output:

```
📊 Loaded embeddings for 3 persons:
  S00001_jereme: 9 embeddings
  S13234_Jin_Rae: 7 embeddings
  S99998_Kim_Jun_Un: 5 embeddings

📈 SIMILARITY STATISTICS
==================================================

🎯 Intra-person similarities (same person):
  S00001_jereme: 0.823 avg (0.756 - 0.891), 36 pairs
  S13234_Jin_Rae: 0.845 avg (0.789 - 0.912), 21 pairs
  S99998_Kim_Jun_Un: 0.798 avg (0.734 - 0.862), 10 pairs

🚫 Inter-person similarities (different persons):
  Overall: 0.234 avg (0.123 - 0.456), 45 comparisons

📊 OVERALL ASSESSMENT:
  Average intra-person similarity: 0.822
  Average inter-person similarity: 0.234
  Separation margin: 0.588
  ✅ Good separation - embeddings are well-clustered
```