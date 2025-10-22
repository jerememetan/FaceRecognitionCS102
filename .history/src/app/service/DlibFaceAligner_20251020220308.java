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
            System.err.println("   Make sure Dlib JavaCPP is in your dependencies");
            isInitialized = false;
        }
    }

    public Mat alignFace(Mat faceImage) {
        if (!isInitialized || faceImage == null || faceImage.empty()) {
            return null;
        }

        try {

            matrix dlibMat = new matrix(faceImage);

            rectangle rect = new rectangle(0, 0, faceImage.width(), faceImage.height());

            full_object_detection shape = predictor.predict(dlibMat, rect);

            Point leftEye = getEyeCenter(shape, 36, 41);
            Point rightEye = getEyeCenter(shape, 42, 47);
            if (leftEye == null || rightEye == null) {
                System.err.println("⚠️ Could not detect eye landmarks");
                return null;
            }

            Mat alignmentMatrix = getAlignmentMatrix(leftEye, rightEye, faceImage.size());

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

    private Point getEyeCenter(full_object_detection shape, int start, int end) {
        try {
            double sumX = 0, sumY = 0;
            int count = 0;
            for (int i = start; i <= end; i++) {
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

    private Mat getAlignmentMatrix(Point leftEye, Point rightEye, Size imgSize) {

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

}