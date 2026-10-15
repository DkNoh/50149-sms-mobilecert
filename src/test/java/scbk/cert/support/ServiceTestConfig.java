package scbk.cert.support;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import scbk.cert.conf.DBConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.util.CreateParser;
import scbk.cert.util.XMLHandler;

/** 실제 서비스 파서로 읽은 테스트 설정을 보관한다. */
public final class ServiceTestConfig {
    private final Path source;
    private final DBConfigBean database;
    private final KCBConfigBean provider;

    private ServiceTestConfig(Path source, DBConfigBean database, KCBConfigBean provider) {
        this.source = source;
        this.database = database;
        this.provider = provider;
    }

    public static ServiceTestConfig load() {
        return load(Paths.get(System.getProperty("mobilecert.test.config", "conf/service-dev.xml")));
    }

    public static ServiceTestConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("서비스 설정 파일이 없습니다. conf/service-dev.xml 또는 mobilecert.test.config를 확인하세요.");
        }
        synchronized (XMLHandler.class) {
            HashMap<String, Object> previous = XMLHandler.map;
            try {
                byte[] bytes = Files.readAllBytes(path);
                String declarations = new String(bytes, StandardCharsets.ISO_8859_1);
                if (declarations.contains("<!DOCTYPE") || declarations.contains("<!ENTITY")) {
                    throw new AssertionError("테스트 설정에는 외부 문서나 엔티티 선언을 사용할 수 없습니다.");
                }
                XMLHandler handler = new XMLHandler();
                new CreateParser(handler).parse(new ByteArrayInputStream(bytes));
                Object database = XMLHandler.map.get("DEFAULT_DB");
                Object provider = XMLHandler.map.get("MOBILE_CERT");
                if (!(database instanceof DBConfigBean) || !(provider instanceof KCBConfigBean)) {
                    throw new AssertionError("DEFAULT_DB와 MOBILE_CERT 설정이 필요합니다.");
                }
                ServiceTestConfig config = new ServiceTestConfig(path.toRealPath(), (DBConfigBean) database, (KCBConfigBean) provider);
                config.validate();
                return config;
            } catch (Exception failure) {
                throw new AssertionError("서비스 설정 파일을 읽을 수 없습니다. 파일 형식과 문자셋을 확인하세요.");
            } finally {
                XMLHandler.map = previous;
            }
        }
    }

    /** 데이터 변경을 수반하는 검증에는 개발 설정만 허용한다. */
    public void requireDevelopmentDatabase() {
        require("service-dev.xml".equalsIgnoreCase(source.getFileName().toString()),
                "DB 연동 테스트에는 service-dev.xml 개발 설정만 사용할 수 있습니다. 운영 설정은 config 프로파일로 검사하세요.");
    }

    public DBConfigBean db() {
        return database;
    }

    public KCBConfigBean kcb() {
        return provider;
    }

    private void validate() {
        require("ORACLE".equalsIgnoreCase(database.getDbType()), "DB는 ORACLE 설정이어야 합니다.");
        require(present(database.getDbUrl()) && database.getDbUrl().startsWith("jdbc:oracle:"),
                "Oracle JDBC 연결 설정이 필요합니다.");
        require(present(database.getJdbcDriver()) && present(database.getDbUser()) && present(database.getDbPassword()),
                "DB 드라이버와 인증 설정이 필요합니다.");
        require(positive(database.getConnection()), "DB 연결 수는 양수여야 합니다.");
        require(identifier(provider.getCertTable()) && identifier(provider.getCertInfoTable()),
                "KCB 월별 테이블 접두어 설정을 확인하세요.");
        require(positive(provider.getTimeOut()) && positive(provider.getRetryCnt()),
                "KCB 제한 시간과 재시도 횟수는 양수여야 합니다.");
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean positive(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_]{0,121}");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}