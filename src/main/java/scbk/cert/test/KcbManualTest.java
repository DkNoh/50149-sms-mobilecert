package scbk.cert.test;

import java.io.Console;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.mci.MCIHeaderBean;
import scbk.cert.bean.mci.MCITransBean;
import scbk.cert.util.CertCharset;
import scbk.cert.util.CommonUtil;

/** Interactive, single-attempt client for the existing local MCI listener. */
public final class KcbManualTest {
    static final int RESPONSE_BYTES = 2048;
    static final int STEP1_LENGTH = 728;
    static final int STEP2_LENGTH = 888;
    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private KcbManualTest() { }

    public static void main(String[] args) {
        int result;
        try {
            Options options = Options.parse(args);
            if (options.help) {
                usage(System.out);
                return;
            }
            Guard guard = new ConfigurationGuard();
            guard.validate(options, null);
            if (options.check) {
                System.out.println("event=manual.configurationChecked network=NOT_CHECKED outcome=NOT_SENT");
                return;
            }
            Console console = System.console();
            if (console == null) {
                System.err.println("event=manual.consoleRequired outcome=NOT_SENT");
                System.exit(2);
                return;
            }
            result = run(options, new ConsolePrompt(console), new LocalExchange(), System.out, guard);
        } catch (ConfigurationFailure failure) {
            System.err.println("event=manual.configurationRejected reason=" + failure.reason + " outcome=NOT_SENT");
            result = 2;
        } catch (InvalidInput failure) {
            System.err.println("event=manual.invalidArguments outcome=NOT_SENT");
            usage(System.err);
            result = 2;
        } catch (RuntimeException failure) {
            System.err.println("event=manual.failed outcome=UNKNOWN retry=MANUAL_ONLY");
            result = 3;
        }
        if (result != 0) System.exit(result);
    }

    static void usage(PrintStream out) {
        out.println("Usage: java ... scbk.cert.test.KcbManualTest --send --port PORT [--timeout-ms MS]");
        out.println("       java ... scbk.cert.test.KcbManualTest --check --port PORT");
        out.println("       java ... scbk.cert.test.KcbManualTest --help");
        out.println("Use -Dscbk.cert.server.type=dev and run from the service working directory (../conf).");
        out.println("Target: 127.0.0.1 only. Interactive console required. No automatic retry.");
        out.println("Timeout: 1000..120000 ms; default 30000 ms per request.");
        out.println("STEP1 requests a real SMS; manually enter the received OTP for STEP2.");
        out.println("Private values are entered without echo and are not printed or saved by this client.");
    }

    static int run(Options options, Prompt prompt, Exchange exchange, PrintStream out, Guard guard) {
        boolean attempted = false;
        try {
            if (!options.send || options.help) throw new InvalidInput();
            Input input = Input.read(prompt);
            guard.validate(options, input);
            out.println("event=manual.ready target=LOOPBACK requests=STEP1_THEN_STEP2 retry=NONE");
            out.println("KCB production SMS will be requested for your own phone; the development DB records the request.");
            out.println("Only continue after the server configuration and the KCB route have been checked.");
            if (!"SEND".equals(prompt.read("Send one real SMS and then verify its OTP? Type SEND: "))) {
                out.println("event=manual.cancelled outcome=NOT_SENT");
                return 0;
            }
            attempted = true;
            Result first = execute(options, input, "STEP1", "", "", exchange, out);
            if (!"0000".equals(first.code)) return 1;
            String otp = prompt.read("Received SMS OTP (6 digits, hidden; blank cancels STEP2): ");
            if (otp == null || otp.length() == 0) {
                out.println("event=manual.cancelled step=STEP2 outcome=NOT_SENT");
                return 1;
            }
            require(otp.matches("[0-9]{6}"));
            Result second = execute(options, input, "STEP2", first.tid, otp, exchange, out);
            return "0000".equals(second.code) ? 0 : 1;
        } catch (ConfigurationFailure failure) {
            out.println("event=manual.configurationRejected reason=" + failure.reason + " outcome=NOT_SENT");
            return 2;
        } catch (InvalidInput failure) {
            out.println("event=manual.invalidInput outcome=" + (attempted ? "STOPPED" : "NOT_SENT"));
            return 2;
        } catch (IOException failure) {
            out.println("event=manual.communicationFailed outcome=UNKNOWN retry=MANUAL_ONLY");
            return 3;
        } catch (InvalidResponse failure) {
            out.println("event=manual.invalidResponse outcome=UNKNOWN retry=MANUAL_ONLY");
            return 3;
        } catch (RuntimeException failure) {
            out.println("event=manual.failed outcome=" + (attempted ? "UNKNOWN" : "NOT_SENT")
                    + " retry=MANUAL_ONLY");
            return 3;
        }
    }

    private static Result execute(Options options, Input input, String step, String tid, String otp,
                                  Exchange exchange, PrintStream out) throws IOException {
        byte[] request = request(input, step, tid, otp);
        byte[] response = null;
        long started = System.nanoTime();
        try {
            response = exchange.exchange(request, options.port, options.timeoutMs);
            Result result = response(response, input, step, tid);
            String outcome = "0000".equals(result.code) ? ("STEP1".equals(step) ? "ACCEPTED" : "SUCCESS") : "REJECTED";
            out.println("event=manual.result step=" + step + " code=" + result.code + " outcome=" + outcome
                    + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return result;
        } finally {
            Arrays.fill(request, (byte) 0);
            if (response != null) Arrays.fill(response, (byte) 0);
        }
    }

    static byte[] request(Input input, String step, String tid, String otp) {
        require("STEP1".equals(step) || "STEP2".equals(step));
        if ("STEP2".equals(step)) require(validTid(tid) && otp.matches("[0-9]{6}"));
        MCIHeaderBean header = new MCIHeaderBean();
        header.setTranCd(input.transactionCode);
        header.setChnlTypCd(input.channel);
        MCITransBean transaction = new MCITransBean();
        KCBCertReqBean body = new KCBCertReqBean();
        body.setMobileStep(step);
        body.setTid(tid);
        body.setMobileComm(input.carrier);
        body.setMobileNum(input.phone);
        // Only the first seven identity digits are used by Gateway.makeUserInfo.
        body.setMobileSsn(input.birthAndSex);
        body.setAuthNumber(otp);
        body.setNameCheckYn("Y");
        body.setBuyer(input.name);
        String data = CommonUtil.byteFiller(input.department, 12) + CommonUtil.byteFiller(input.office, 12)
                + CommonUtil.setFiller(164) + body.getTransaction();
        byte[] payload = encode(header.getTransaction() + transaction.getTransaction() + '\u001e' + '\u001e'
                + data + '\u001e');
        require(payload.length == 625);
        byte[] frame = new byte[633];
        byte[] prefix = encode("00000625");
        System.arraycopy(prefix, 0, frame, 0, prefix.length);
        System.arraycopy(payload, 0, frame, 8, payload.length);
        Arrays.fill(payload, (byte) 0);
        return frame;
    }

    static Result response(byte[] frame, Input input, String expectedStep, String expectedTid) {
        check(frame != null && frame.length == RESPONSE_BYTES);
        int length = responseLength(frame);
        check(frame[158] == 0x1e && frame[8 + length - 1] == 0x1f);
        if ("STEP1".equals(expectedStep)) check(length == STEP1_LENGTH);
        for (int index = 8 + length; index < frame.length; index++) check(frame[index] == 0);
        check(field(frame, 56, 12).equals(input.transactionCode));
        check(field(frame, 85, 3).equals(input.channel));
        check(field(frame, 263, 12).equals(input.department));
        check(field(frame, 275, 12).equals(input.office));
        String code = field(frame, 159, 4);
        check(code.matches("[A-Z0-9]{4}") && code.equals(field(frame, 287, 4)));
        check(field(frame, 451, 5).equals(expectedStep));
        String tid = field(frame, 456, 24);
        if ("STEP2".equals(expectedStep)) check(validTid(expectedTid) && expectedTid.equals(tid));
        if ("0000".equals(code)) {
            check(validTid(tid) && field(frame, 571, 4).equals(code));
            check(field(frame, 481, 12).equals(input.phone));
            check(field(frame, 493, 13).equals(input.birthAndSex));
            check(field(frame, 555, 15).equals(input.name));
            check(length == ("STEP1".equals(expectedStep) ? STEP1_LENGTH : STEP2_LENGTH));
        }
        return new Result(code, tid, field(frame, 570, 1));
    }

    static int responseLength(byte[] frame) {
        check(frame != null && frame.length >= 8);
        int length = 0;
        for (int index = 0; index < 8; index++) {
            check(frame[index] >= '0' && frame[index] <= '9');
            length = length * 10 + frame[index] - '0';
        }
        check(length == STEP1_LENGTH || length == STEP2_LENGTH);
        return length;
    }

    private static String field(byte[] frame, int offset, int length) {
        try {
            return CertCharset.EUC_KR.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(frame, offset, length)).toString().trim();
        } catch (CharacterCodingException failure) {
            throw new InvalidResponse();
        }
    }

    static byte[] encode(String value) {
        try {
            ByteBuffer buffer = CertCharset.EUC_KR.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        } catch (CharacterCodingException failure) {
            throw new InvalidInput();
        }
    }

    private static boolean validTid(String value) {
        return value != null && value.matches("CERT[0-9]{20}");
    }

    private static void require(boolean valid) {
        if (!valid) throw new InvalidInput();
    }

    private static void check(boolean valid) {
        if (!valid) throw new InvalidResponse();
    }

    interface Prompt { String read(String label); }
    interface Exchange { byte[] exchange(byte[] request, int port, int timeoutMs) throws IOException; }
    interface Guard { void validate(Options options, Input input); }

    static final class ConfigurationGuard implements Guard {
        @Override public void validate(Options options, Input input) {
            validateConfiguration(options, input, Paths.get("../conf/service-dev.xml"), Paths.get("../conf/lguPlus"),
                    System.getProperty("scbk.cert.server.type"));
        }
    }

    static void validateConfiguration(Options options, Input input, Path file, Path routes, String serverType) {
        configuration("dev".equals(serverType), "SERVER_TYPE_MUST_BE_DEV");
        byte[] bytes = null;
        try {
            configuration(Files.isRegularFile(file), "SERVICE_DEV_XML_REQUIRED");
            configuration("service-dev.xml".equals(file.toRealPath().getFileName().toString()), "SERVICE_DEV_XML_REQUIRED");
            configuration(Files.size(file) <= 1048576, "CONFIGURATION_TOO_LARGE");
            bytes = Files.readAllBytes(file);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException failure) throws SAXParseException { throw failure; }
                @Override public void fatalError(SAXParseException failure) throws SAXParseException { throw failure; }
            });
            Document document = builder.parse(new ByteArrayInputStream(bytes));
            Element listener = entity(document, "XPAY_LEASED", "scbk.cert.conf.ConfigBean");
            Element provider = entity(document, "MOBILE_CERT", "scbk.cert.conf.KCBConfigBean");
            String configuredPort = value(listener, "serverPort");
            configuration(configuredPort.matches("[0-9]{1,5}"), "INVALID_SERVER_PORT");
            configuration(Integer.parseInt(configuredPort) == options.port, "SERVER_PORT_MISMATCH");
            configuration("N".equals(value(provider, "step1Mode")) && "N".equals(value(provider, "step2Mode")),
                    "REAL_RESPONSE_MODES_REQUIRED");
            if (Files.exists(routes)) {
                configuration(Files.isRegularFile(routes) && Files.size(routes) <= 1048576, "INVALID_ROUTE_FILE");
                try (BufferedReader reader = Files.newBufferedReader(routes, CertCharset.EUC_KR)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        configuration(input == null || !line.equals(input.transactionCode), "TRANSACTION_ROUTES_TO_LGU");
                    }
                }
            }
        } catch (ConfigurationFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ConfigurationFailure("CONFIGURATION_UNREADABLE_OR_INVALID");
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private static Element entity(Document document, String name, String type) {
        NodeList entities = document.getElementsByTagName("entity");
        Element match = null;
        for (int index = 0; index < entities.getLength(); index++) {
            Element candidate = (Element) entities.item(index);
            if (name.equals(candidate.getAttribute("name"))) {
                configuration(match == null && type.equals(candidate.getAttribute("class")), "INVALID_SERVICE_ENTITY");
                match = candidate;
            }
        }
        configuration(match != null, "MISSING_SERVICE_ENTITY");
        return match;
    }

    private static String value(Element entity, String name) {
        NodeList matches = entity.getElementsByTagName(name);
        configuration(matches.getLength() == 1 && matches.item(0).getParentNode() == entity, "INVALID_SERVICE_FIELD");
        return matches.item(0).getTextContent().trim();
    }

    private static void configuration(boolean valid, String reason) {
        if (!valid) throw new ConfigurationFailure(reason);
    }

    static final class ConsolePrompt implements Prompt {
        private final Console console;
        ConsolePrompt(Console console) { this.console = console; }
        @Override public String read(String label) {
            char[] value = console.readPassword("%s", label);
            if (value == null) return null;
            try { return new String(value); }
            finally { Arrays.fill(value, '\0'); }
        }
    }

    static final class LocalExchange implements Exchange {
        @Override public byte[] exchange(byte[] request, int port, int timeoutMs) throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port),
                        remaining(deadline));
                socket.setSoTimeout(remaining(deadline));
                socket.getOutputStream().write(request);
                socket.getOutputStream().flush();
                byte[] response = new byte[RESPONSE_BYTES];
                try {
                    readFully(socket, response, 0, 8, deadline);
                    responseLength(response);
                    readFully(socket, response, 8, RESPONSE_BYTES - 8, deadline);
                    return response;
                } catch (IOException | RuntimeException failure) {
                    Arrays.fill(response, (byte) 0);
                    throw failure;
                }
            }
        }
    }

    static void readFully(Socket socket, byte[] target, int offset, int length, long deadline) throws IOException {
        InputStream input = socket.getInputStream();
        int position = 0;
        while (position < length) {
            socket.setSoTimeout(remaining(deadline));
            int count = input.read(target, offset + position, length - position);
            if (count < 0) throw new IOException("Incomplete response");
            if (count == 0) throw new IOException("Empty response read");
            position += count;
        }
    }

    static int remaining(long deadline) throws SocketTimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new SocketTimeoutException("Request deadline reached");
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos)));
    }

    static final class Options {
        final boolean send;
        final boolean check;
        final boolean help;
        final int port;
        final int timeoutMs;
        Options(boolean send, boolean check, boolean help, int port, int timeoutMs) {
            this.send = send; this.check = check; this.help = help; this.port = port; this.timeoutMs = timeoutMs;
        }
        static Options parse(String[] args) {
            if (args.length == 1 && "--help".equals(args[0])) return new Options(false, false, true, 0, 30000);
            boolean send = false;
            boolean check = false;
            int port = 0;
            int timeout = 30000;
            boolean timeoutSet = false;
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--send".equals(argument)) { require(!send); send = true; }
                else if ("--check".equals(argument)) { require(!check); check = true; }
                else if ("--port".equals(argument)) {
                    require(port == 0 && ++index < args.length);
                    port = number(args[index], 1, 65535);
                } else if ("--timeout-ms".equals(argument)) {
                    require(!timeoutSet && ++index < args.length);
                    timeoutSet = true;
                    timeout = number(args[index], 1000, 120000);
                } else throw new InvalidInput();
            }
            require(send != check && port > 0 && (!check || !timeoutSet));
            return new Options(send, check, false, port, timeout);
        }
        private static int number(String value, int min, int max) {
            require(value.matches("[0-9]{1,6}"));
            int result = Integer.parseInt(value);
            require(result >= min && result <= max);
            return result;
        }
    }

    static final class Input {
        final String transactionCode, department, office, channel, carrier, phone, birthAndSex, name;
        Input(String transactionCode, String department, String office, String channel, String carrier,
              String phone, String birthAndSex, String name) {
            this.transactionCode = identifier(transactionCode, 12);
            this.department = identifier(department, 12);
            this.office = identifier(office, 12);
            this.channel = identifier(channel, 3);
            require(carrier != null && carrier.matches("[123567]"));
            require(phone != null && phone.matches("01[016789][0-9]{7,8}"));
            require(birthAndSex != null && birthAndSex.matches("[0-9]{6}[1-8]"));
            char sex = birthAndSex.charAt(6);
            String century = "1256".indexOf(sex) >= 0 ? "19" : "20";
            try { LocalDate.parse(century + birthAndSex.substring(0, 6), BIRTH_DATE); }
            catch (RuntimeException failure) { throw new InvalidInput(); }
            require(name != null && !name.isEmpty() && name.equals(name.trim()));
            for (int index = 0; index < name.length(); index++) {
                char value = name.charAt(index);
                require(!Character.isISOControl(value) && value != '\u2028' && value != '\u2029');
            }
            require(encode(name).length <= 15);
            this.carrier = carrier; this.phone = phone; this.birthAndSex = birthAndSex; this.name = name;
        }
        private static String identifier(String value, int max) {
            require(value != null && value.matches("[A-Za-z0-9_-]{1," + max + "}"));
            return value;
        }
        static Input read(Prompt prompt) {
            return new Input(prompt.read("KCB-routed MCI transaction code (max 12 ASCII bytes): "),
                    prompt.read("Active development department code (max 12 ASCII bytes): "),
                    prompt.read("Office/staff code (max 12 ASCII bytes): "),
                    prompt.read("MCI channel type (max 3 ASCII bytes): "),
                    prompt.read("Legacy carrier code (1/2/3/5/6/7): "),
                    prompt.read("Your own phone number (digits only, hidden): "),
                    prompt.read("Birth YYMMDD + identity category digit (7 digits only, hidden): "),
                    prompt.read("Registered subscriber name (max 15 EUC-KR bytes, hidden): "));
        }
    }

    static final class Result {
        final String code, tid, certMode;
        Result(String code, String tid, String certMode) { this.code = code; this.tid = tid; this.certMode = certMode; }
    }
    static final class InvalidInput extends RuntimeException { private static final long serialVersionUID = 1L; }
    static final class InvalidResponse extends RuntimeException { private static final long serialVersionUID = 1L; }
    static final class ConfigurationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;
        ConfigurationFailure(String reason) { this.reason = reason; }
    }
}
