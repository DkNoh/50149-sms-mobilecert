package scbk.cert.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Transport regression checks using finite memory streams only. */
class SocketManagerRegressionTest {
    @Test
    void fixedLengthReadCollectsFragmentedBytesWithoutChangingTheirValues() throws IOException {
        byte[] payload = {0x41, (byte) 0xb0, (byte) 0xa1, 0x20, 0x00, (byte) 0xff};
        SocketManager manager = new SocketManager();
        manager.inputStream = new FragmentedInputStream(payload, 1);

        assertArrayEquals(payload, manager.recv(payload.length));
        assertEquals(-1, manager.inputStream.read());
    }

    @Test
    void fixedLengthReadsKeepConcatenatedHeaderAndBodyBoundaries() throws IOException {
        byte[] header = "00000004".getBytes(StandardCharsets.US_ASCII);
        byte[] firstBody = "ABCD".getBytes(StandardCharsets.US_ASCII);
        byte[] secondBody = "WXYZ".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(header);
        wire.write(firstBody);
        wire.write(header);
        wire.write(secondBody);
        SocketManager manager = new SocketManager();
        manager.inputStream = new FragmentedInputStream(wire.toByteArray(), 3);

        assertArrayEquals(header, manager.recv(header.length));
        assertArrayEquals(firstBody, manager.recv(firstBody.length));
        assertArrayEquals(header, manager.recv(header.length));
        assertArrayEquals(secondBody, manager.recv(secondBody.length));
        assertEquals(-1, manager.inputStream.read());
    }

    @Test
    void fixedLengthReadPropagatesTimeoutAfterPartialResponse() {
        SocketTimeoutException timeout = new SocketTimeoutException("synthetic read timeout");
        SocketManager manager = new SocketManager();
        manager.inputStream = new InputStream() {
            private boolean firstByte = true;

            @Override
            public int read() throws IOException {
                if (firstByte) {
                    firstByte = false;
                    return 0x41;
                }
                throw timeout;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) {
                    return 0;
                }
                bytes[offset] = (byte) read();
                return 1;
            }
        };

        assertSame(timeout, assertThrows(SocketTimeoutException.class, () -> manager.recv(4)));
    }

    @Test
    void sendWritesAllBytesAndFlushesThem() throws IOException {
        byte[] payload = {0x00, 0x20, (byte) 0xb0, (byte) 0xa1, (byte) 0xff};
        RecordingOutputStream output = new RecordingOutputStream();
        SocketManager manager = new SocketManager();
        manager.outputStream = output;

        manager.send(payload);

        assertArrayEquals(payload, output.toByteArray());
        assertEquals(1, output.flushCount);
        assertTrue(output.bytesPresentAtFlush);
    }

    @Test
    void sendPropagatesFlushFailure() {
        IOException failure = new IOException("synthetic flush failure");
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                throw failure;
            }
        };
        SocketManager manager = new SocketManager();
        manager.outputStream = output;

        assertSame(failure, assertThrows(IOException.class, () -> manager.send(new byte[] {0x41})));
        assertArrayEquals(new byte[] {0x41}, output.toByteArray());
    }

    private static final class FragmentedInputStream extends ByteArrayInputStream {
        private final int chunkSize;

        private FragmentedInputStream(byte[] bytes, int chunkSize) {
            super(bytes);
            this.chunkSize = chunkSize;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, chunkSize));
        }
    }

    private static final class RecordingOutputStream extends ByteArrayOutputStream {
        private int flushCount;
        private boolean bytesPresentAtFlush;

        @Override
        public void flush() {
            flushCount++;
            bytesPresentAtFlush = size() > 0;
        }
    }
}
