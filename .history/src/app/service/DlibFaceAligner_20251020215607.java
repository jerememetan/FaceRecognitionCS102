package app.service;

import org.bytedeco.dlib.*;
import org.bytedeco.javacpp.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.bytedeco.dlib.global.dlib.*;


public class DlibFaceAligner {
    private shape_predictor predictor;
    private boolean isInitialized = false;
    private static final Point2f LEFT_EYE_TARGET = new Point2f(0.35f, 0.35f);
    private static final Point2f RIGHT_EYE_TARGET = new Point2f(0.65f, 0.35f);

}

public DlibFaceAligner() {
try {
String modelPath = "data/resources/shape_predictor_68_face_landmarks.dat";
if (new java.io.File(modelPath).exists()) {
predictor = new shape_predictor();
deserialize(modelPath, predictor);
isInitialized = true;
System.out.println("✅ Dlib shape predictor loaded successfully");
} else {
System.err.println("❌ Dlib model not found: " + modelPath);
isInitialized = false;
}
4. Add Dlib JNI bindings to your project
Required Files:
shape_predictor_68_face_landmarks.dat → Place in data/resources/
Dlib native libraries for your platform (Windows/Linux/Mac)
Complete Refactored Files
DlibFaceAligner.java (NEW FILE)
} catch (Exception e) {
System.err.println("❌ Failed to load Dlib face aligner: " + e.getMessage());
System.err.println(" Make sure Dlib JavaCPP is in your dependencies");
isInitialized = false;
}
}