package app.service.recognition;

import java.nio.ByteBuffer;

import ConfigurationAndLogging.AppConfig;
import ConfigurationAndLogging.AppLogger;

/**
 * Helper methods for converting between the various embedding formats stored on
 * disk and the representations used at runtime. Encapsulating this logic keeps
 * the rest of the recognition pipeline focused on behaviour instead of
 * byte-level detail.
 */
final class RecognitionEmbeddingUtils {

    private RecognitionEmbeddingUtils() {
    }

    static double[] decodeToDouble(byte[] embedding) {
        if (embedding == null) {
            return null;
        }

        int configuredSize = Math.max(1, AppConfig.getInstance().getEmbeddingSize());

        try {
            if (embedding.length == configuredSize * Float.BYTES) {
                return floatsToDoubles(readFloatArray(embedding));
            }

            if (embedding.length == configuredSize * Double.BYTES) {
                return readDoubleArray(embedding);
            }

            if (embedding.length % Float.BYTES == 0) {
                return floatsToDoubles(readFloatArray(embedding));
            }

            if (embedding.length % Double.BYTES == 0) {
                return readDoubleArray(embedding);
            }
        } catch (Exception e) {
            AppLogger.warn("Failed to decode embedding: " + e.getMessage());
        }

        return null;
    }

    static byte[] encodeFromDouble(double[] vector, boolean preferFloat) {
        if (vector == null) {
            return null;
        }

        int configuredSize = Math.max(1, AppConfig.getInstance().getEmbeddingSize());

        if (preferFloat) {
            ByteBuffer buffer = ByteBuffer.allocate(configuredSize * Float.BYTES);
            for (int i = 0; i < configuredSize; i++) {
                float value = (i < vector.length) ? (float) vector[i] : 0.0f;
                buffer.putFloat(value);
            }
            return buffer.array();
        }

        ByteBuffer buffer = ByteBuffer.allocate(configuredSize * Double.BYTES);
        for (int i = 0; i < configuredSize; i++) {
            double value = (i < vector.length) ? vector[i] : 0.0;
            buffer.putDouble(value);
        }
        return buffer.array();
    }

    static void normalizeL2InPlace(double[] vector) {
        if (vector == null || vector.length == 0) {
            return;
        }
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    static double cosineSimilarity(byte[] queryEmbedding, double[] reference) {
        double[] queryVector = decodeToDouble(queryEmbedding);
        if (queryVector == null || reference == null || queryVector.length != reference.length) {
            return 0.0;
        }

        normalizeL2InPlace(queryVector);

        double dot = 0.0;
        for (int i = 0; i < queryVector.length; i++) {
            dot += queryVector[i] * reference[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static float[] readFloatArray(byte[] data) {
        if (data == null || data.length == 0 || data.length % Float.BYTES != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int count = data.length / Float.BYTES;
        float[] floats = new float[count];
        for (int i = 0; i < count; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private static double[] readDoubleArray(byte[] data) {
        if (data == null || data.length == 0 || data.length % Double.BYTES != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int count = data.length / Double.BYTES;
        double[] doubles = new double[count];
        for (int i = 0; i < count; i++) {
            doubles[i] = buffer.getDouble();
        }
        return doubles;
    }

    private static double[] floatsToDoubles(float[] floats) {
        if (floats == null) {
            return null;
        }
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}
