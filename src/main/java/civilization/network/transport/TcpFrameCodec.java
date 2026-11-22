package civilization.network.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class TcpFrameCodec {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_MAX_FRAME_BYTES = 4 * 1024 * 1024;

    // PHASE 3 - Added in Phase 3
    private TcpFrameCodec() {
    }

    // PHASE 3 - Added in Phase 3
    public static String read(DataInputStream input, int maxFrameBytes) throws IOException {
        Objects.requireNonNull(input, "input");
        requireLimit(maxFrameBytes);
        int length;
        try {
            length = input.readInt();
        } catch (EOFException endOfStream) {
            throw endOfStream;
        }
        if (length <= 0 || length > maxFrameBytes) {
            throw new IOException("Invalid TCP frame length: " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // PHASE 3 - Added in Phase 3
    public static void write(
            DataOutputStream output, String json, int maxFrameBytes) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(json, "json");
        requireLimit(maxFrameBytes);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maxFrameBytes) {
            throw new IOException("Invalid TCP frame length: " + bytes.length);
        }
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    // PHASE 3 - Added in Phase 3
    private static void requireLimit(int maxFrameBytes) {
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("Maximum frame size must be positive");
        }
    }
}
