package app.service;
import org.bytedeco.dlib.*;
import org.bytedeco.javacpp.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.bytedeco.dlib.global.dlib.*;
/**
* DlibFaceAligner - Face Alignment using Dlib Landmarks
*
* Implements OUTER_EYES_AND_NOSE alignment strategy for OpenFace nn4.small2.v1 model.
* This normalizes face poses by aligning eyes and nose to standard positions.
*
* Reference: OpenFace documentation
* https://cmusatyalab.github.io/openface/models-and-accuracies/
*/
public class DlibFaceAligner {}