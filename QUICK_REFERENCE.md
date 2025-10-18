# Quick Reference: What Changed

## 🔧 Parameter Changes at a Glance

### Image Quality (ImageProcessor.java)
| Parameter | Old | New | Change |
|-----------|-----|-----|--------|
| Sharpness | 80 | **120** | +50% stricter |
| Min Brightness | 30 | **40** | +33% |
| Max Brightness | 230 | **220** | -4% |
| Contrast | 20 | **25** | +25% |

### Capture (FaceDetection.java)
| Parameter | Old | New | Change |
|-----------|-----|-----|--------|
| Confidence | 0.5 | **0.6** | +20% |
| Interval (ms) | 900 | **1200** | +33% |
| Attempts | 12x | **15x** | +25% |

### Recognition (NewFaceRecognitionDemo.java)
| Parameter | Old | New | Change |
|-----------|-----|-----|--------|
| Consistency Window | 5 | **7** | +40% |
| Min Consistent | 3 | **5** | +67% |
| Penalty Weight | 0.20 | **0.25** | +25% |
| Min Margin % | 10% | **12%** | +20% |
| Strong Margin % | 10% | **15%** | +50% |

### Defaults (FaceCaptureDialog.java)
| Parameter | Old | New | Change |
|-----------|-----|-----|--------|
| Default Images | 15 | **20** | +33% |
| Max Option | 20 | **25** | New |

---

## 📍 Where to Find Things

| What | File | Line(s) |
|------|------|---------|
| Quality thresholds | ImageProcessor.java | ~14-18 |
| Capture timing | FaceDetection.java | ~27-32 |
| Consistency params | NewFaceRecognitionDemo.java | ~48-63 |
| Decision logic | NewFaceRecognitionDemo.java | ~622-643 |
| Capture defaults | FaceCaptureDialog.java | ~189-194 |

---

## 🎯 Testing Checklist

- [ ] Compile: `gradlew.bat build`
- [ ] Delete old data: `data/facedata/[StudentID]/`
- [ ] Capture: 20 images, good lighting
- [ ] Check: Quality scores >85%?
- [ ] Test: Run recognition
- [ ] Monitor: Console logs for decisions
- [ ] Measure: Accuracy, false +/-, stability

---

## 🔄 Quick Rollback Values

If too strict, change back to:
```java
// ImageProcessor.java
MIN_SHARPNESS_THRESHOLD = 80.0;
MIN_BRIGHTNESS = 30.0;
MAX_BRIGHTNESS = 230.0;
MIN_CONTRAST = 20.0;

// FaceDetection.java
MIN_CONFIDENCE_SCORE = 0.5;
CAPTURE_INTERVAL_MS = 900;
CAPTURE_ATTEMPT_MULTIPLIER = 12;

// NewFaceRecognitionDemo.java
CONSISTENCY_WINDOW = 5;
CONSISTENCY_MIN_COUNT = 3;
PENALTY_WEIGHT = 0.20;
MIN_RELATIVE_MARGIN_PCT = 0.10;
STRONG_MARGIN_THRESHOLD = 0.10;
```

---

## 📖 Documentation Files

| File | Purpose |
|------|---------|
| `EXPERIMENTAL_GUIDE.md` | Complete testing protocol |
| `IMPLEMENTATION_COMPLETE.md` | What was done, next steps |
| `QUICK_REFERENCE.md` | This file - quick lookup |
| `CRITICAL_BUGS.md` | Bugs that were fixed |
| `HONEST_ASSESSMENT.md` | Evidence vs speculation |

---

## 💡 Key Insights

**What's proven:**
- ✅ Glare reduction helps with glasses
- ✅ Embedding validation prevents crashes
- ✅ Memory leak fix prevents crashes

**What's experimental:**
- 🧪 Higher quality thresholds
- 🧪 Stricter consistency requirements
- 🧪 Longer capture intervals
- 🧪 More training images

**Bottom line**: Bug fixes are solid. Everything else needs your testing to validate!

---

**Print this for quick reference during testing!**
