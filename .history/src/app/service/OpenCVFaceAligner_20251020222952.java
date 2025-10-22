package app.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.face.Face;
import org.opencv.face.Facemark;

import org.opencv.objdetect.CascadeClassifier;
import java.util.ArrayList;
import java.util.List;

public class OpenCVFaceAligner {
    private Facemark facemark;
    private boolean isInitialized = false;
    // Target eye positions for alignment (normalized 0-1)
    private static final double LEFT_EYE_X = 0.35;
    private static final double LEFT_EYE_Y = 0.35;
    private static final double RIGHT_EYE_X = 0.65;
    private static final double RIGHT_EYE_Y = 0.35;

public OpenCVFaceAligner() {
try {
String modelPath = "data/resources/lbfmodel.yaml";
if (new java.io.File(modelPath).exists()) {
// Create FacemarkLBF instance
facemark = Face.createFacemarkLBF();
facemark.loadModel(modelPath);
isInitialized = true;
System.out.println("✅ OpenCV FacemarkLBF loaded successfully");
} else {
System.err.println("❌ LBF model not found: " + modelPath);
isInitialized = false;
}
} catch (Exception e) {
System.err.println("❌ Failed to load OpenCV FacemarkLBF: " + e.getMessage())
System.err.println(" Make sure OpenCV contrib modules are included");
isInitialized = false;
}
}


public Mat alignFace(Mat faceImage, Rect faceRect) {
if (!isInitialized || faceImage == null || faceImage.empty()) {
return null;
}
try {
// Convert to grayscale for landmark detection
Mat gray = new Mat();
if (faceImage.channels() &gt; 1) {
Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
} else {
gray = faceImage.clone();
}
// Prepare face rectangles for landmark detection
MatOfRect faces = new MatOfRect();
if (faceRect != null) {
faces.fromList(java.util.Arrays.asList(faceRect));
} else {
// Use entire image as face region
faces.fromList(java.util.Arrays.asList(
new Rect(0, 0, faceImage.width(), faceImage.height())
));
}
// Detect facial landmarks (68 points)
List&lt;MatOfPoint2f&gt; landmarks = new ArrayList&lt;&gt;();
boolean success = facemark.fit(gray, faces, landmarks);
gray.release();
faces.release();
if (!success || landmarks.isEmpty()) {
System.err.println("⚠️ Facial landmark detection failed");
return null;
}
// Extract landmark points
MatOfPoint2f landmarksMat = landmarks.get(0);
Point[] points = landmarksMat.toArray();
landmarksMat.release();
if (points.length &lt; 68) {
System.err.println("⚠️ Insufficient landmarks detected: " + points.length
return null;
}
// Calculate eye centers
// Left eye: landmarks 36-41 (indices in 0-based array)
// Right eye: landmarks 42-47
Point leftEye = calculateEyeCenter(points, 36, 41);
Point rightEye = calculateEyeCenter(points, 42, 47);
if (leftEye == null || rightEye == null) {
System.err.println("⚠️ Could not calculate eye centers");
return null


}
