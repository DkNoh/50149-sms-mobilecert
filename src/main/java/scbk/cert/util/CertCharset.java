package scbk.cert.util;

import java.nio.charset.Charset;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/** Production source/protocol charset. EUC-KR is distinct from MS949. */
public final class CertCharset {
    public static final Charset EUC_KR = Charset.forName("EUC-KR");

    private CertCharset() { }

    public static void configureStandardStreams() {
        // Retain legacy stream bytes independently of the JVM default charset.
        System.out.flush();
        System.err.flush();
        try {
            // The charset-name overload is available on both Java 8 and Java 21.
            System.setOut(new PrintStream(System.out, true, EUC_KR.name()));
            System.setErr(new PrintStream(System.err, true, EUC_KR.name()));
        } catch (UnsupportedEncodingException impossible) {
            // EUC_KR was already resolved successfully above; never use a default.
            throw new IllegalStateException("Resolved EUC-KR charset is unavailable", impossible);
        }
    }
}
