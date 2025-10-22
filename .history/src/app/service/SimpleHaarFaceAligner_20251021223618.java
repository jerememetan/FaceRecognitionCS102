package app.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple face aligner using only Haar cascade eye detection
 * NO external dependencies - uses standard OpenCV only
 */
public class FaceAligner {
    
    private CascadeClassifier eyeDetector;
    private boolean isInitialized = false;
    
    // Target eye positions for 96x96 aligned face
    // Left eye at (30, 35), right eye at (66, 35) - eyes on same horizontal line
    private static final Point LEFT_EYE_TARGET = new Point(30, 35);
    private static final Point RIGHT_EYE_TARGET = new Point(66, 35);
    private static final Size OUTPUT_SIZE = new Size(96, 96);
    
    // For debugging - enable to see eye detection results
    private boolean debugMode = false;
    
    public SimpleHaarFaceAligner() {
        initialize();
    }
    
    private void initialize() {
        try {
            // Path to Haar cascade - included with OpenCV
            String eyeCascadePath = "data/resources/haarcascade_eye.xml";
            
            eyeDetector = new CascadeClassifier(eyeCascadePath);
            
            if (eyeDetector.empty()) {
                System.err.println("ERROR: Could not load eye cascade from: " + eyeCascadePath);
                System.err.println("Make sure haarcascade_eye.xml is in data/resources/");
                isInitialized = false;
            } else {
                System.out.println("✓ SimpleHaarFaceAligner initialized successfully");
                isInitialized = true;
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize SimpleHaarFaceAligner: " + e.getMessage());
            isInitialized = false;
        }
    }
    
    /**
     * Aligns a face image using simple eye detection
     * @param faceImage Input face ROI (already cropped to face bounding box)
     * @param faceRect Original face bounding box (for reference)
     * @return Aligned 96x96 face image, or null if alignment fails
     */
    public Mat align(Mat faceImage, Rect faceRect) {
        if (!isInitialized) {
            return fallbackAlignment(faceImage);
        }
        
        if (faceImage == null || faceImage.empty()) {
            System.err.println("Empty face image provided to aligner");
            return null;
        }
        
        try {
            // Convert to grayscale for eye detection
            Mat gray = new Mat();
            if (faceImage.channels() == 3) {
                Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = faceImage.clone();
            }
            
            // Detect eyes in the face region
            Point leftEye = null;
            Point rightEye = null;
            
            // Try to detect eyes
            MatOfRect eyes = new MatOfRect();
            eyeDetector.detectMultiScale(gray, eyes, 1.1, 5, 0, 
                                        new Size(20, 20), new Size(80, 80));
            
            Rect[] eyesArray = eyes.toArray();
            
            if (eyesArray.length >= 2) {
                // Found at least 2 eyes - select the best pair
                List<Point> eyeCenters = new ArrayList<>();
                
                for (Rect eye : eyesArray) {
                    // Calculate eye center
                    Point center = new Point(
                        eye.x + eye.width / 2.0,
                        eye.y + eye.height / 2.0
                    );
                    eyeCenters.add(center);
                }
                
                // Find the best pair (two eyes at similar y-coordinate in upper half of face)
                double bestScore = Double.MAX_VALUE;
                int bestLeft = -1, bestRight = -1;
                
                for (int i = 0; i < eyeCenters.size(); i++) {
                    for (int j = i + 1; j < eyeCenters.size(); j++) {
                        Point eye1 = eyeCenters.get(i);
                        Point eye2 = eyeCenters.get(j);
                        
                        // Calculate horizontal distance and vertical alignment
                        double dx = Math.abs(eye1.x - eye2.x);
                        double dy = Math.abs(eye1.y - eye2.y);
                        
                        // Good eye pair should have:
                        // 1. Reasonable horizontal distance (20-70% of face width)
                        // 2. Similar y-coordinates (within 15% of face height)
                        // 3. Both in upper half of face
                        double distanceRatio = dx / faceImage.width();
                        double alignmentRatio = dy / faceImage.height();
                        
                        boolean inUpperHalf = (eye1.y < faceImage.height() * 0.6) && 
                                             (eye2.y < faceImage.height() * 0.6);
                        
                        if (distanceRatio > 0.2 && distanceRatio < 0.7 && 
                            alignmentRatio < 0.15 && inUpperHalf) {
                            
                            // Score based on alignment and distance
                            double score = alignmentRatio * 2.0 + Math.abs(0.45 - distanceRatio);
                            
                            if (score < bestScore) {
                                bestScore = score;
                                if (eye1.x < eye2.x) {
                                    bestLeft = i;
                                    bestRight = j;
                                } else {
                                    bestLeft = j;
                                    bestRight = i;
                                }
                            }
                        }
                    }
                }
                
                if (bestLeft != -1 && bestRight != -1) {
                    leftEye = eyeCenters.get(bestLeft);
                    rightEye = eyeCenters.get(bestRight);
                }
            }
            
            gray.release();
            eyes.release();
            
            // If we found both eyes, perform alignment
            if (leftEye != null && rightEye != null) {
                if (debugMode) {
                    System.out.println("Eyes detected: Left=" + leftEye + ", Right=" + rightEye);
                }
                return alignUsingEyes(faceImage, leftEye, rightEye);
            } else {
                if (debugMode) {
                    System.out.println("Could not detect 2 eyes, using fallback alignment");
                }
                return fallbackAlignment(faceImage);
            }
            
        } catch (Exception e) {
            System.err.println("Eye detection failed: " + e.getMessage());
            return fallbackAlignment(faceImage);
        }
    }
    
    /**
     * Performs alignment given detected eye positions
     */
    private Mat alignUsingEyes(Mat faceImage, Point leftEye, Point rightEye) {
        try {
            // Calculate angle between eyes
            double dY = rightEye.y - leftEye.y;
            double dX = rightEye.x - leftEye.x;
            double angle = Math.toDegrees(Math.atan2(dY, dX));
            
            // Calculate distance between eyes
            double eyeDistance = Math.sqrt(dX * dX + dY * dY);
            
            // Calculate desired distance in output image
            double desiredEyeDistance = RIGHT_EYE_TARGET.x - LEFT_EYE_TARGET.x;
            double scale = desiredEyeDistance / eyeDistance;
            
            // Calculate center point between eyes
            Point eyesCenter = new Point(
                (leftEye.x + rightEye.x) / 2.0,
                (leftEye.y + rightEye.y) / 2.0
            );
            
            // Calculate desired center in output
            Point desiredCenter = new Point(
                (LEFT_EYE_TARGET.x + RIGHT_EYE_TARGET.x) / 2.0,
                (LEFT_EYE_TARGET.y + RIGHT_EYE_TARGET.y) / 2.0
            );
            
            // Create rotation matrix for rotation + scale around eye center
            Mat rotationMatrix = Imgproc.getRotationMatrix2D(eyesCenter, angle, scale);
            
            // Adjust translation to move eyes to desired position
            double tx = desiredCenter.x - eyesCenter.x * scale;
            double ty = desiredCenter.y - eyesCenter.y * scale;
            
            rotationMatrix.put(0, 2, rotationMatrix.get(0, 2) + tx);
            rotationMatrix.put(1, 2, rotationMatrix.get(1, 2) + ty);
            
            // Apply transformation
            Mat aligned = new Mat();
            Imgproc.warpAffine(faceImage, aligned, rotationMatrix, OUTPUT_SIZE,
                              Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
            
            rotationMatrix.release();
            
            return aligned;
            
        } catch (Exception e) {
            System.err.println("Alignment transformation failed: " + e.getMessage());
            return fallbackAlignment(faceImage);
        }
    }
    
    /**
     * Fallback when eye detection fails - just center crop and resize
     */
    private Mat fallbackAlignment(Mat faceImage) {
        if (faceImage == null || faceImage.empty()) {
            return new Mat();
        }
        
        try {
            Mat aligned = new Mat();
            Imgproc.resize(faceImage, aligned, OUTPUT_SIZE, 0, 0, Imgproc.INTER_CUBIC);
            return aligned;
        } catch (Exception e) {
            System.err.println("Fallback alignment failed: " + e.getMessage());
            return faceImage.clone();
        }
    }
    
    /**
     * Enable debug output for testing
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }
    
    /**
     * Check if aligner is properly initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Clean up resources
     */
    public void release() {
        if (eyeDetector != null && !eyeDetector.empty()) {
            // CascadeClassifier doesn't need explicit release in Java
            eyeDetector = null;
        }
    }
}
