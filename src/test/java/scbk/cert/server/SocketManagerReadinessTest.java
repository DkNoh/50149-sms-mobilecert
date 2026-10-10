package scbk.cert.server;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Desired failure handling, intentionally failing on the current implementation.
 * These checks expose existing defects; they do not certify the defects as compatible behavior.
 * All streams are finite in-memory fakes and no socket connects to a network.
 */
@Tag("readiness")
class SocketManagerReadinessTest {
    @Test
    void incompleteFixedLengthResponseMustFailInsteadOfReturningZeroPadding() {
        SocketManager manager = new SocketManager();
        manager.inputStream = new ByteArrayInputStream(new byte[] {0x41, 0x42});

        assertThrows(IOException.class, () -> manager.recv(4),
                "A 2-byte response cannot satisfy an expected 4-byte authentication frame");
    }

    @Test
    void immediateEofMustFailInsteadOfReturningAnEmptyPaddedFrame() {
        SocketManager manager = new SocketManager();
        manager.inputStream = new ByteArrayInputStream(new byte[0]);

        assertThrows(IOException.class, () -> manager.recv(8),
                "An EOF before the length header must not look like a complete header");
    }

    @Test
    void cleanupMustCloseOutputAndSocketEvenWhenInputCloseFails() {
        CloseFailingInputStream input = new CloseFailingInputStream(new byte[] {0x52});
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        MemorySocket memorySocket = new MemorySocket(input, output);
        SocketManager manager = new SocketManager() {
            @Override
            public void connect() throws IOException {
                setSocket(memorySocket);
            }
        };

        assertThrows(IOException.class, () -> manager.connect(new byte[] {0x51}, 1));
        assertAll(
                () -> assertTrue(input.closeAttempted, "Input cleanup must be attempted"),
                () -> assertTrue(output.closeAttempted, "Output cleanup must survive input.close failure"),
                () -> assertTrue(memorySocket.closeAttempted, "Socket cleanup must survive input.close failure"));
    }

    @Test
    void recvAllMustNotTreatAvailableZeroAsEndOfResponse() throws IOException {
        byte[] expected = {0x41, 0x42, 0x43, 0x44};
        SocketManager manager = new SocketManager();
        manager.inputStream = new ByteArrayInputStream(expected) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 2));
            }

            @Override
            public synchronized int available() {
                // available() == 0 permits a later read to return data; it is not EOF.
                return 0;
            }
        };

        assertArrayEquals(expected, manager.recvAll(),
                "A temporarily empty receive buffer must not truncate the response");
    }

    private static final class CloseFailingInputStream extends ByteArrayInputStream {
        private boolean closeAttempted;

        private CloseFailingInputStream(byte[] response) {
            super(response);
        }

        @Override
        public void close() throws IOException {
            closeAttempted = true;
            throw new IOException("synthetic input close failure");
        }
    }

    private static final class CloseTrackingOutputStream extends ByteArrayOutputStream {
        private boolean closeAttempted;

        @Override
        public void close() {
            closeAttempted = true;
        }
    }

    private static final class MemorySocket extends Socket {
        private final InputStream input;
        private final OutputStream output;
        private boolean closeAttempted;

        private MemorySocket(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public synchronized void close() {
            closeAttempted = true;
        }
    }
}
