package app.service;

import org.bytedeco.dlib.*;
import org.bytedeco.javacpp.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.bytedeco.dlib.global.dlib.*;

/**
 * DlibFaceAligner - Face Alignment using Dlib Landmarks
 *
 * Implements OUTER_EYES_AND_NOSE alignment strategy for OpenFace nn4.small2.v1
 * model.
 * This normalizes face poses by aligning eyes and nose to standard positions.
 *
 * Reference: OpenFace documentation
 * https://cmusatyalab.github.io/openface/models-and-accuracies/
 */
public class DlibFaceAligner {
    private shape_predictor predictor;
    private boolean isInitialized = false;
    // Target landmark positions for alignment (based on OpenFace requirements)
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
System.err.println(" Download from: http://dlib.net/files/shape_predict
isInitialized = false;
}
} catch (Exception e) {
System.err.println("❌ Failed to load Dlib face aligner: " + e.getMessage());
System.err.println(" Make sure Dlib JavaCPP is in your dependencies");
isInitialized = false;
}
}

/**
* Align face using OUTER_EYES_AND_NOSE landmarks.
* Returns aligned face or null if alignment fails.
*
* @param faceImage Face ROI image (BGR)
* @return Aligned face image or null
*/
public Mat alignFace(Mat faceImage) {
if (!isInitialized || faceImage == null || faceImage.empty()) {
return null;
}
try {
// Convert OpenCV Mat to Dlib matrix
opencv_core.Mat dlibMat = matToD libMat(faceImage);
// Create face rectangle (entire image)
rectangle rect = new rectangle(0, 0, faceImage.width(), faceImage.height());
// Detect facial landmarks
full_object_detection shape = predictor.call(dlibMat, rect);
// Extract eye landmarks (indices based on 68-point model)
// Left eye: average of points 36-41
// Right eye: average of points 42-47
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

/**
* Calculate average position of eye landmarks.
*/
private Point getEyeCenter(full_object_detection shape, int start, int end) {
try {
double sumX = 0, sumY = 0;
int count = 0;
for (int i = start; i &lt;= end; i++) {
dlib.point pt = shape.part(i);
sumX += pt.x();
sumY += pt.y();
count++;
}
return new Point(sumX / count, sumY / count);
} catch (Exception e) {
return null;
}
}

    /**
     * Calculate affine transformation matrix for alignment.
     */
    private Mat getAlignmentMatrix(Point leftEye, Point rightEye, Size imgSize) {
        // Calculate angle between eyes
        double dx = rightEye.x - leftEye.x;
        double dy = rightEye.y - leftEye.y;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        // Calculate eye center
        Point eyesCenter = new Point((leftEye.x + rightEye.x) / 2.0,
                (leftEye.y + rightEye.y) / 2.0);
        // Get rotation matrix
        Mat M = Imgproc.getRotationMatrix2D(eyesCenter, angle, 1.0);
        // Add translation to center eyes at target positions
        double targetX = imgSize.width * 0.5;
        double targetY = imgSize.height * 0.35;
        double tx = targetX - eyesCenter.x;
        double ty = targetY - eyesCenter.y;
        M.put(0, 2, M.get(0, 2)[0] + tx);
        M.put(1, 2, M.get(1, 2)[0] + ty);
        return M;
    }

/**
* Convert OpenCV Mat to Dlib matrix.
*/
private opencv_core.Mat matToDlibMat(Mat opencvMat) {
// Convert to grayscale for landmark detection
Mat gray = new Mat();
if (opencvMat.channels() &gt; 1) {
Imgproc.cvtColor(opencvMat, gray, Imgproc.COLOR_BGR2GRAY);
} else {
gray = opencvMat.clone();
}
// Create Dlib matrix
opencv_core.Mat dlibMat = new opencv_core.Mat(
gray.rows(), gray.cols(), opencv_core.CV_8UC1);
// Copy data
byte[] data = new byte[(int) (gray.total() * gray.elemSize())];
gray.get(0, 0, data);
dlibMat.data().put(data);
gray.release();
return dlibMat;
}

    public void release() {
        if (predictor != null) {
            predictor.close();
        }
    }
}