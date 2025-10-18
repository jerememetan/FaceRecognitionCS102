# Critical Performance Issue - Camera Lock Contention

## Problem
The capture loop is performing **60-110ms of heavy processing** while effectively blocking camera access, causing preview stuttering.

## Root Cause
```java
while (capturing) {
    synchronized (cameraLock) {
        camera.read(frame);  // Lock acquired
    }
    // Camera lock released BUT frame processing blocks next read
    
    preprocessForRecognition(faceROI);  // 20-40ms
    detectLandmarks(processed);         // 30-50ms  
    validateQuality(processed);         // 10-20ms
    
    Thread.sleep(900);  // Preview blocked during processing + sleep
}
```

## Solution
Defer **ALL** processing to after capture:

**Phase 1: Capture (FAST)**
```java
while (capturing) {
    synchronized (cameraLock) {
        camera.read(frame);
    }
    if (hasFace) {
        Mat faceROI = extractROI(frame).clone();
        rawFaces.add(faceROI);  // Just store, no processing
    }
    frame.release();
}
```

**Phase 2: Process (AFTER capture, no camera)**
```java
for (Mat rawFace : rawFaces) {
    Mat processed = preprocessForRecognition(rawFace);
    byte[] embedding = generateEmbedding(processed);
    // Save...
}
```

This ensures preview gets camera access every ~900ms instead of being blocked for 60-110ms per iteration.
