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
        } catch (Exception e) {
            System.err.println("❌ Failed to load Dlib face aligner: " + e.getMessage());
            System.err.println(" Make sure Dlib JavaCPP is in your dependencies");
            isInitialized = false;
        }
    }

    public Mat alignFace(Mat faceImage) {
if (!isInitialized || faceImage == null || faceImage.empty()) {
return null;
}
try {
// Convert OpenCV Mat to Dlib matrix
opencv_core.Mat dlibMat = matToDlibMat(faceImage);
// Create face rectangle (entire image)
rectangle rect = new rectangle(0, 0, faceImage.width(), faceImage.height());
// Detect facial landmarks
full_object_detection shape = predictor.call(dlibMat, rect);

Point leftEye = getEyeCenter(shape, 36, 41);
Point rightEye = getEyeCenter(shape, 42, 47);
if (leftEye == null || rightEye == null) {
System.err.println("⚠️ Could not detect eye landmarks");
return null;
}
// Calculate transformation matrix for alignment
Mat alignmentMatrix = getAlignmentMatrix(leftEye, rightEye, faceImage.size())
// Apply affine transformation
Mat aligned = new Mat();
Imgproc.warpAffine(faceImage, aligned, alignmentMatrix, faceImage.size(),
Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
alignmentMatrix.release();
return aligned;
} catch (Exception e) {
System.err.println("❌ Face alignment failed: " + e.getMessage());
return null;
}
}
}
