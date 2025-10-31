package service.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods for working with embedding vectors.
 */
public final class EmbeddingVectorUtils {

    private EmbeddingVectorUtils() {
        // Utility class
    }

    public static float[] toFloatArray(byte[] data, int expectedLength) {
        if (data == null || data.length != expectedLength) {
            return null;
        }
        float[] vector = new float[data.length / 4];
        ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(vector);
        return vector;
    }

    public static double[] toDoubleArray(byte[] data) {
        if (data == null || data.length % 8 != 0) {
            return null;
        }
        double[] vector = new double[data.length / 8];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getDouble();
        }
        return vector;
    }

    public static double magnitude(float[] vector) {
        if (vector == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    public static double magnitude(double[] vector) {
        if (vector == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    public static double dot(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static byte[] doublesToBytes(double[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 8).order(ByteOrder.BIG_ENDIAN);
        for (double v : values) {
            buffer.putDouble(v);
        }
        return buffer.array();
    }

    public static byte[] floatsToBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float v : values) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
}







