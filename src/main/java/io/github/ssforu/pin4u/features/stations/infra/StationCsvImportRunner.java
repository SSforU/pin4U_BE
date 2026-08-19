package io.github.ssforu.pin4u.features.stations.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "pin4u.stations.import.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class StationCsvImportRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ResourceLoader resourceLoader;

    @Value("${app.stations.seed.csv-path:}")
    private String csvPath;

    private static final double SEOUL_LAT_MIN = 37.4133;
    private static final double SEOUL_LAT_MAX = 37.7151;
    private static final double SEOUL_LNG_MIN = 126.7341;
    private static final double SEOUL_LNG_MAX = 127.2693;

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private record StationRow(String code, String name, String line, BigDecimal lat, BigDecimal lng) {}

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource csv = resolveCsv();
        if (csv == null || !csv.exists()) {
            log.info("[stations] CSV 미발견 → 주입 스킵");
            return;
        }

        List<StationRow> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            log.info("[stations] 파싱 결과 0건 → 종료");
            return;
        }

        // JdbcTemplate.batchUpdate + INSERT ... ON CONFLICT (upsert)
        // 행별 find+save(N select + N insert) 대신 단일 배치로 처리
        String upsertSql = """
                INSERT INTO stations (code, name, line, lat, lng)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    name = EXCLUDED.name,
                    line = EXCLUDED.line,
                    lat  = EXCLUDED.lat,
                    lng  = EXCLUDED.lng
                """;

        int[] results = jdbc.batchUpdate(upsertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StationRow r = rows.get(i);
                ps.setString(1, r.code());
                ps.setString(2, r.name());
                ps.setString(3, r.line());
                ps.setBigDecimal(4, r.lat());
                ps.setBigDecimal(5, r.lng());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });

        log.info("[stations] 벌크 upsert 완료: {}건 처리", results.length);
    }

    private List<StationRow> parseCsv(Resource csv) throws Exception {
        List<StationRow> rows = new ArrayList<>();
        Map<String, Integer> lineSeq = preloadLineSeq();

        try (var br = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return rows;

            String[] heads = header.split(",", -1);
            Map<String, Integer> idx = indexOf(heads,
                    List.of("호선", "line"),
                    List.of("역명", "name"),
                    List.of("WGS84위도", "lat"),
                    List.of("WGS84경도", "lng")
            );
            if (idx.values().stream().anyMatch(Objects::isNull)) {
                log.error("[stations] 열 매핑 실패. 헤더={}", Arrays.toString(heads));
                return rows;
            }

            String row;
            while ((row = br.readLine()) != null) {
                String[] t = row.split(",", -1);
                if (t.length < heads.length) continue;

                String rawLine = t[idx.get("line")].trim();
                String line = normalizeLine(rawLine);
                Integer lineNo = extractLineNo(line);
                if (lineNo == null || lineNo < 1 || lineNo > 9) continue;

                String name = t[idx.get("name")].trim();
                BigDecimal lat = parseDecimal(t[idx.get("lat")]);
                BigDecimal lng = parseDecimal(t[idx.get("lng")]);
                if (lat == null || lng == null) continue;
                if (!inSeoul(lat.doubleValue(), lng.doubleValue())) continue;

                int next = lineSeq.compute(line, (k, v) -> (v == null ? 0 : v) + 1);
                String code = buildCode(lineNo, next);
                rows.add(new StationRow(code, name, line, lat, lng));
            }
        }
        return rows;
    }

    private Map<String, Integer> preloadLineSeq() {
        Map<String, Integer> seq = new HashMap<>();
        try {
            jdbc.query("SELECT code, line FROM stations", rs -> {
                String code = rs.getString("code");
                String line = rs.getString("line");
                Integer lineNo = extractLineNo(line);
                if (lineNo == null) return;
                Integer parsed = parseSeqFromCode(code, lineNo);
                if (parsed == null) return;
                seq.merge(line, parsed, Math::max);
            });
        } catch (Exception e) {
            log.warn("[stations] preloadLineSeq failed, starting from 0", e);
        }
        return seq;
    }

    private Resource resolveCsv() {
        try {
            if (csvPath != null && !csvPath.isBlank()) {
                if (csvPath.startsWith("classpath:")) return resourceLoader.getResource(csvPath);
                return resourceLoader.getResource("file:" + csvPath);
            }
            Resource r = new ClassPathResource("data/서울교통공사_노선별 지하철역 정보.csv");
            if (r.exists()) return r;
            r = new ClassPathResource("data/stations.csv");
            return r.exists() ? r : null;
        } catch (Exception e) {
            log.warn("[stations] CSV 리소스 해석 실패", e);
            return null;
        }
    }

    private static Map<String, Integer> indexOf(String[] heads, List<String> line, List<String> name, List<String> lat, List<String> lng) {
        Map<String, Integer> m = new HashMap<>();
        m.put("line", findIndex(heads, line));
        m.put("name", findIndex(heads, name));
        m.put("lat", findIndex(heads, lat));
        m.put("lng", findIndex(heads, lng));
        return m;
    }

    private static Integer findIndex(String[] heads, List<String> cands) {
        for (int i = 0; i < heads.length; i++) {
            String h = heads[i].trim();
            for (String c : cands) if (h.equalsIgnoreCase(c)) return i;
        }
        return null;
    }

    private static Integer parseSeqFromCode(String code, int lineNo) {
        if (code == null || code.length() < 5 || code.charAt(0) != 'S') return null;
        try {
            int ln = Integer.parseInt(code.substring(1, 3));
            if (ln != lineNo) return null;
            return Integer.parseInt(code.substring(3));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeLine(String raw) {
        if (raw == null) return "";
        String s = raw.replace(" ", "");
        Matcher m = DIGITS.matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1)) + "호선";
        return s;
    }

    private static Integer extractLineNo(String line) {
        if (line == null) return null;
        Matcher m = DIGITS.matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static boolean inSeoul(double lat, double lng) {
        return lat >= SEOUL_LAT_MIN && lat <= SEOUL_LAT_MAX
                && lng >= SEOUL_LNG_MIN && lng <= SEOUL_LNG_MAX;
    }

    private static BigDecimal parseDecimal(String v) {
        try {
            if (v == null || v.isBlank()) return null;
            return new BigDecimal(v.trim()).setScale(6, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildCode(int lineNo, int seq) {
        return "S" + String.format("%02d", lineNo) + String.format("%02d", seq);
    }
}
