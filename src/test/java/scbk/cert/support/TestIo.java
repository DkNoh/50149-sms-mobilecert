package scbk.cert.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** 독립된 테스트에서 사용하는 자바 8 호환 입출력 기능이다. */
public final class TestIo {
    private TestIo() { }

    public static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    public static String readString(Path path, Charset charset) throws IOException {
        return new String(Files.readAllBytes(path), charset);
    }

    public static void writeString(Path path, String text, Charset charset) throws IOException {
        Files.write(path, text.getBytes(charset));
    }
}
