
import org.opencv.core.Core;

public class OpenCvTest {
    public static void main(String[] args) {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            System.out.println("OpenCV native library loaded successfully!");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV native library!");
            e.printStackTrace();
        }
    }
}
