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
System.err.println(" Download from: https://github.com/kurnianggoro/GSO
isInitialized = false;
}
} catch (Exception e) {
System.err.println("❌ Failed to load OpenCV FacemarkLBF: " + e.getMessage())
System.err.println(" Make sure OpenCV contrib modules are included");
isInitialized = false;
}
}
