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

}