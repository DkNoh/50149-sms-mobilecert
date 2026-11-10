<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.io.*,java.net.*,java.nio.*,java.nio.charset.*,java.nio.file.*,java.security.*,java.time.*,java.time.format.*,java.util.*,java.util.concurrent.*,javax.xml.XMLConstants,javax.xml.parsers.*,org.w3c.dom.*,org.xml.sax.SAXParseException,org.xml.sax.helpers.DefaultHandler" %>
<%!
    // EDIT ONLY THIS PATH: directory containing the running service's service-dev.xml.
    private static final String MOBILECERT_CONF_DIR = "";
    private static final int MOBILECERT_PORT = 38610;
    private static final int TIMEOUT_MS = 30000;
    private static final long STATE_TTL_MS = 10 * 60 * 1000L;
    private static final long OTP_TTL_MS = 5 * 60 * 1000L;
    static final int RESPONSE_BYTES = 2048, STEP1_LENGTH = 728, STEP2_LENGTH = 888;
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, State> STATES = new HashMap<String, State>();

    static byte[] request(Input input, String step, String tid, String otp) {
        require("STEP1".equals(step) || "STEP2".equals(step));
        if ("STEP2".equals(step)) require(validTid(tid) && otp != null && otp.matches("[0-9]{6}"));
        byte[] frame = new byte[633];
        Arrays.fill(frame, (byte) ' ');
        put(frame, 0, 8, "00000625");
        put(frame, 56, 12, input.transactionCode);
        put(frame, 85, 3, input.channel);
        put(frame, 88, 2, "00");
        put(frame, 99, 4, "0000");
        put(frame, 110, 4, "0000");
        frame[158] = 0x1e; frame[159] = 0x1e;
        put(frame, 160, 12, input.department);
        put(frame, 172, 12, input.office);
        put(frame, 348, 5, step);
        put(frame, 353, 24, tid);
        put(frame, 377, 1, input.carrier);
        put(frame, 378, 12, input.phone);
        put(frame, 390, 13, input.birthAndSex);
        put(frame, 413, 6, otp);
        put(frame, 451, 1, "Y");
        put(frame, 452, 15, input.name);
        frame[632] = 0x1e;
        return frame;
    }
    private static void put(byte[] frame, int offset, int width, String value) {
        byte[] data = encode(value);
        try { require(data.length <= width); System.arraycopy(data, 0, frame, offset, data.length); }
        finally { Arrays.fill(data, (byte) 0); }
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
            return EUC_KR.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(frame, offset, length)).toString().trim();
        } catch (CharacterCodingException failure) {
            throw new InvalidResponse();
        }
    }

    static byte[] encode(String value) {
        try {
            ByteBuffer buffer = EUC_KR.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
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
    static void configuration(String directory, int port, Input input) {
        configuration(directory != null && !directory.trim().isEmpty(), "CONF_PATH_REQUIRED");
        Path folder = Paths.get(directory);
        configuration(folder.isAbsolute(), "ABSOLUTE_CONF_PATH_REQUIRED");
        Path file = folder.resolve("service-dev.xml");
        Path routes = folder.resolve("lguPlus");
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
            configuration(Integer.parseInt(configuredPort) == port, "SERVER_PORT_MISMATCH");
            configuration("N".equals(value(provider, "step1Mode")) && "N".equals(value(provider, "step2Mode")),
                    "REAL_RESPONSE_MODES_REQUIRED");
            if (Files.exists(routes)) {
                configuration(Files.isRegularFile(routes) && Files.size(routes) <= 1048576, "INVALID_ROUTE_FILE");
                try (BufferedReader reader = Files.newBufferedReader(routes, EUC_KR)) {
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
        static byte[] exchange(byte[] request, int port, int timeoutMs) throws IOException {
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

    static final class State {
        String csrf = token(), stage = "READY", code = "READY", message = "인증정보를 입력해 주세요.";
        Input input;
        String tid;
        long expires, lastAccess = System.currentTimeMillis();
        volatile boolean busy;
        void clear() { input = null; tid = null; expires = 0; }
    }
    private static String token() {
        byte[] bytes = new byte[24]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static State state(String key) {
        synchronized (STATES) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, State>> it = STATES.entrySet().iterator();
            while (it.hasNext()) {
                State old = it.next().getValue();
                if (!old.busy && now - old.lastAccess > STATE_TTL_MS) { old.clear(); it.remove(); }
            }
            State s = STATES.get(key);
            if (s == null) {
                if (STATES.size() >= 64) throw new ConfigurationFailure("PAGE_BUSY");
                s = new State(); STATES.put(key, s);
            }
            s.lastAccess = now;
            return s;
        }
    }
    private static void expire(State s) {
        if (s.input != null && System.currentTimeMillis() >= s.expires) {
            s.clear(); s.stage = "READY"; s.code = "EXPIRED";
            s.message = "인증 대기 시간이 지났습니다. 새 테스트를 시작해 주세요.";
        }
    }
    static String handle(State s, String action, Map<String, String> parameters, String directory, int port) {
        synchronized (s) {
            expire(s);
            if ("init".equals(action)) return json(true, s.code, s.message, s);
            if (!s.csrf.equals(parameters.get("csrf")))
                return json(false, "STALE_REQUEST", "이미 처리했거나 만료된 요청입니다. 페이지를 새로고침해 주세요.", s);
            s.csrf = token();
            if ("reset".equals(action)) {
                s.clear(); s.stage = "READY"; s.code = "READY"; s.message = "새 인증 테스트를 시작할 수 있습니다.";
                return json(true, s.code, s.message, s);
            }
            boolean attempted = false;
            byte[] outgoing = null, incoming = null;
            try {
                if ("send".equals(action)) {
                    if (!"READY".equals(s.stage)) return json(false, "ALREADY_STARTED", "새 테스트를 시작한 뒤 발송해 주세요.", s);
                    Input input = new Input(parameters.get("tranCd"), parameters.get("deptCode"), parameters.get("officeCode"),
                        parameters.get("channel"), parameters.get("carrier"), parameters.get("phone"), parameters.get("identity"), parameters.get("name"));
                    configuration(directory, port, input);
                    outgoing = request(input, "STEP1", "", "");
                    attempted = true; s.busy = true;
                    incoming = exchange(outgoing, port, TIMEOUT_MS);
                    Result result = response(incoming, input, "STEP1", "");
                    s.code = result.code;
                    if ("0000".equals(result.code)) {
                        s.input = input; s.tid = result.tid; s.expires = System.currentTimeMillis() + OTP_TTL_MS;
                        s.stage = "OTP"; s.message = "문자 발송 요청이 정상 처리됐습니다. 받은 인증번호를 입력해 주세요.";
                    } else {
                        s.clear(); s.stage = "DONE"; s.message = "문자 발송 요청이 거절됐습니다. 응답 코드를 확인해 주세요.";
                    }
                    return json("0000".equals(result.code), s.code, s.message, s);
                }
                if ("verify".equals(action)) {
                    if (!"OTP".equals(s.stage) || s.input == null) return json(false, "NO_PENDING_AUTH", "먼저 인증문자를 발송해 주세요.", s);
                    String otp = parameters.get("otp");
                    require(otp != null && otp.matches("[0-9]{6}"));
                    configuration(directory, port, s.input);
                    outgoing = request(s.input, "STEP2", s.tid, otp);
                    attempted = true; s.busy = true;
                    incoming = exchange(outgoing, port, TIMEOUT_MS);
                    Result result = response(incoming, s.input, "STEP2", s.tid);
                    s.clear(); s.stage = "DONE"; s.code = result.code;
                    s.message = "0000".equals(result.code) ? "본인인증이 정상 완료됐습니다." : "인증에 실패했습니다. 응답 코드를 확인해 주세요.";
                    return json("0000".equals(result.code), s.code, s.message, s);
                }
                return json(false, "INVALID_ACTION", "지원하지 않는 요청입니다.", s);
            } catch (ConfigurationFailure error) {
                return json(false, error.reason, "JSP 상단 설정 경로와 서비스의 포트·응답 모드·트랜코드를 확인해 주세요.", s);
            } catch (InvalidInput error) {
                return json(false, "INVALID_INPUT", "입력 형식을 확인해 주세요. 이름은 EUC-KR 15바이트 이내여야 합니다.", s);
            } catch (Exception error) {
                if (attempted) {
                    s.clear(); s.stage = "UNKNOWN"; s.code = "UNKNOWN";
                    s.message = "처리 결과를 확인하지 못했습니다. 자동 재전송하지 않습니다. 문자 수신 여부와 서비스 로그를 확인해 주세요.";
                } else {
                    s.code = "REQUEST_FAILED"; s.message = "요청을 준비하지 못했습니다.";
                }
                return json(false, s.code, s.message, s);
            } finally {
                s.busy = false; s.lastAccess = System.currentTimeMillis();
                if (outgoing != null) Arrays.fill(outgoing, (byte) 0);
                if (incoming != null) Arrays.fill(incoming, (byte) 0);
            }
        }
    }
    private static String quote(String value) {
        StringBuilder text = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\"' || c == '\\') text.append('\\').append(c);
            else if (c < 32) text.append(' ');
            else text.append(c);
        }
        return text.append('\"').toString();
    }
    static String json(boolean ok, String code, String message, State s) {
        return "{\"ok\":" + ok + ",\"code\":" + quote(code) + ",\"message\":" + quote(message)
            + ",\"stage\":" + quote(s.stage) + ",\"csrf\":" + quote(s.csrf) + "}";
    }
%>
<%
    request.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    String action = request.getParameter("action");
    boolean isGet = "GET".equals(request.getMethod());
    boolean isPost = "POST".equals(request.getMethod());
    {
        response.setContentType("application/json; charset=UTF-8");
        if ((!isGet && !isPost) || (isGet && !"init".equals(action)) || (isPost && "init".equals(action))) {
            response.setStatus(405); out.print("{\"ok\":false,\"code\":\"METHOD_NOT_ALLOWED\"}"); return;
        }
        if (isPost && (request.getContentLengthLong() > 8192 || request.getQueryString() != null
                || request.getContentType() == null || !request.getContentType().startsWith("application/x-www-form-urlencoded"))) {
            response.setStatus(400); out.print("{\"ok\":false,\"code\":\"INVALID_REQUEST\"}"); return;
        }
        try {
            String key;
            synchronized (session) {
                key = (String) session.getAttribute("scbk.kcb.test.page.key");
                if (key == null) { key = token(); session.setAttribute("scbk.kcb.test.page.key", key); }
            }
            State current = state(key);
            Map<String, String> fields = new HashMap<String, String>();
            if (isPost) {
                for (String field : new String[] {"csrf","tranCd","deptCode","officeCode","channel","carrier","phone","identity","name","otp"}) {
                    String[] values = request.getParameterValues(field);
                    if (values != null && (values.length != 1 || values[0].length() > 128)) throw new InvalidInput();
                    fields.put(field, values == null ? null : values[0]);
                }
            }
            out.print(handle(current, action, fields, MOBILECERT_CONF_DIR, MOBILECERT_PORT));
        } catch (Exception error) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"code\":\"PAGE_UNAVAILABLE\",\"message\":\"페이지를 새로고침해 주세요.\",\"stage\":\"UNKNOWN\",\"csrf\":\"\"}");
        }
        return;
    }
%>
