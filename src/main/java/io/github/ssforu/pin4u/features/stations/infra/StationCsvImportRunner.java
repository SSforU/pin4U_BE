package io.github.ssforu.pin4u.features.stations.infra;

import io.github.ssforu.pin4u.features.stations.domain.Station;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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

    private final StationRepository stationRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.stations.seed.csv-path:}")
    private String csvPath;

    // 서울권 대략 경계 박스 (WGS84)
    private static final double SEOUL_LAT_MIN = 37.4133;
    private static final double SEOUL_LAT_MAX = 37.7151;
    private static final double SEOUL_LNG_MIN = 126.7341;
    private static final double SEOUL_LNG_MAX = 127.2693;

    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Resource csv = resolveCsv();
        if (csv == null || !csv.exists()) {
            log.info("[stations] CSV 미발견 → 주입 스킵 (classpath:data/서울교통공사_노선별 지하철역 정보.csv 또는 app.stations.seed.csv-path 사용 가능)");
            return;
        }

        // 선내 시퀀스 초기화(기존 code에서 이어받기)
        Map<String, Integer> lineSeq = preloadLineSeq();

        int imported = 0, updated = 0, skipped = 0;

        try (var br = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) {
                log.warn("[stations] 빈 CSV → 종료");
                return;
            }
            String[] heads = header.split(",", -1);
            Map<String,Integer> idx = indexOf(heads,
                    List.of("호선","line"),
                    List.of("역명","name"),
                    List.of("WGS84위도","lat"),
                    List.of("WGS84경도","lng")
            );
            if (idx.values().stream().anyMatch(Objects::isNull)) {
                log.error("[stations] 열 매핑 실패. 헤더={}", Arrays.toString(heads));
                return;
            }

            String row;
            while ((row = br.readLine()) != null) {
                String[] t = row.split(",", -1);
                if (t.length < heads.length) { skipped++; continue; }

                String rawLine = t[idx.get("line")].trim();
                String line = normalizeLine(rawLine);           // "7호선" 표준화
                Integer lineNo = extractLineNo(line);
                if (lineNo == null || lineNo < 1 || lineNo > 9) { skipped++; continue; } // 1~9호선만

                String name = t[idx.get("name")].trim();
                BigDecimal lat = parseDecimal(t[idx.get("lat")]);
                BigDecimal lng = parseDecimal(t[idx.get("lng")]);
                if (lat == null || lng == null) { skipped++; continue; }
                if (!inSeoul(lat.doubleValue(), lng.doubleValue())) { skipped++; continue; }

                // (name,line) 업서트 — 정확 일치 우선
                var ex = stationRepository.findByNameAndLine(name, line)
                        .or(() -> stationRepository.findAllByName(name).stream()
                                .filter(s -> s.getLine().equalsIgnoreCase(line)).findFirst());

                if (ex.isPresent()) {
                    Station s = ex.get();
                    boolean changed = false;
                    if (s.getLat() == null || s.getLat().compareTo(lat) != 0) { s.setLat(lat); changed = true; }
                    if (s.getLng() == null || s.getLng().compareTo(lng) != 0) { s.setLng(lng); changed = true; }
                    if (changed) { stationRepository.save(s); updated++; } else { skipped++; }
                } else {
                    int next = lineSeq.compute(line, (k,v) -> (v==null?0:v)+1);
                    String code = buildCode(lineNo, next);
                    Station s = new Station();
                    s.setCode(code);
                    s.setName(name);
                    s.setLine(line);
                    s.setLat(lat);
                    s.setLng(lng);
                    stationRepository.save(s);
                    imported++;
                }
            }
        }

        log.info("[stations] 주입 완료: imported={}, updated={}, skipped={}", imported, updated, skipped);
    }

    private Resource resolveCsv() {
        try {
            if (csvPath != null && !csvPath.isBlank()) {
                if (csvPath.startsWith("classpath:")) return resourceLoader.getResource(csvPath);
                return resourceLoader.getResource("file:" + csvPath);
            }
            // 기본: 공공 CSV(클래스패스)
            Resource r = new ClassPathResource("data/서울교통공사_노선별 지하철역 정보.csv");
            if (r.exists()) return r;
            // 폴백: 기존 우리의 스냅샷
            r = new ClassPathResource("data/stations.csv");
            return r.exists() ? r : null;
        } catch (Exception e) {
            log.warn("[stations] CSV 리소스 해석 실패", e);
            return null;
        }
    }

    private static Map<String,Integer> indexOf(String[] heads, List<String> line, List<String> name, List<String> lat, List<String> lng) {
        Map<String,Integer> m = new HashMap<>();
        m.put("line", findIndex(heads, line));
        m.put("name", findIndex(heads, name));
        m.put("lat",  findIndex(heads, lat));
        m.put("lng",  findIndex(heads, lng));
        return m;
    }

    private static Integer findIndex(String[] heads, List<String> cands) {
        for (int i=0;i<heads.length;i++) {
            String h = heads[i].trim();
            for (String c : cands) if (h.equalsIgnoreCase(c)) return i;
        }
        return null;
    }

    private Map<String,Integer> preloadLineSeq() {
        Map<String,Integer> seq = new HashMap<>();
        for (Station s : stationRepository.findAll()) {
            Integer lineNo = extractLineNo(s.getLine());
            if (lineNo == null) continue;
            Integer parsed = parseSeqFromCode(s.getCode(), lineNo);
            if (parsed == null) continue;
            String line = s.getLine();
            seq.put(line, Math.max(seq.getOrDefault(line, 0), parsed));
        }
        return seq;
    }

    private static Integer parseSeqFromCode(String code, int lineNo) {
        if (code == null || code.length() < 5 || code.charAt(0) != 'S') return null;
        try {
            int ln = Integer.parseInt(code.substring(1,3));
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
