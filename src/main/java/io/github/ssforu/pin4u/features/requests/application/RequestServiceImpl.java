package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final SlugGenerator slugGenerator;
    private final RequestPlaceAggregateRepository rpaRepository;

    // ✅ 레거시 코드 -> 정규 코드 간이 매핑 (필요 시 확장)
    private static final Map<String, String> LEGACY_ALIAS = Map.of(
            "7-733", "S0701"
    );

    // JSON 문자열에서 단순 추출용 패턴
    private static final Pattern CODE_P = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NAME_P = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern LINE_P = Pattern.compile("\\\"line\\\"\\s*:\\s*\\\"?([0-9A-Za-z가-힣]+)\\\"?");

    // 레거시 코드(숫자-숫자), 예: 7-733, 111-1
    private static final Pattern LEGACY_CODE_P = Pattern.compile("^\\d+-\\d+$");

    public RequestServiceImpl(RequestRepository requestRepository,
                              StationRepository stationRepository,
                              SlugGenerator slugGenerator,
                              RequestPlaceAggregateRepository rpaRepository) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.slugGenerator = slugGenerator;
        this.rpaRepository = rpaRepository;
    }

    @Override
    public RequestDtos.CreatedRequestDTO create(String ownerNickname, String stationCodeRaw, String requestMessage) {
        // ✅ 저장에 사용할 스테이션 코드를 '반드시' 확정 (존재/매핑 보장)
        String normalizedCode = resolveStationCodeOr400(stationCodeRaw);

        // slug seed는 확정된 코드 사용
        String slug = slugGenerator.generate(normalizedCode);

        Request saved = requestRepository.save(
                new Request(slug, ownerNickname, normalizedCode, requestMessage)
        );

        return new RequestDtos.CreatedRequestDTO(
                saved.getSlug(),
                saved.getOwnerNickname(),
                saved.getStationCode(),
                saved.getRequestMessage(),
                saved.getCreatedAt()
        );
    }

    /** 단건 상세 (slug로 조회) — recommend_count는 합계로 계산 */
    @Override
    @Transactional(readOnly = true)
    public RequestDtos.ListItem get(String slug) {
        Request r = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("request not found: " + slug));

        Station st = null;
        if (r.getStationCode() != null && !r.getStationCode().isBlank()) {
            st = stationRepository.findByCode(r.getStationCode()).orElse(null);
        }
        String stationName = (st != null) ? st.getName() : null;
        String stationLine = (st != null) ? st.getLine() : null;

        int total = Optional.ofNullable(rpaRepository.sumByRequestId(slug)).orElse(0L).intValue();

        return new RequestDtos.ListItem(
                r.getSlug(),          // slug
                stationName,          // station_name
                stationLine,          // station_line
                null,                 // road_address_name (현재는 null/생략)
                total,                // ✅ 합계
                r.getCreatedAt()
        );
    }

    /** 리스트 — recommend_count는 합계로 계산 */
    @Override
    @Transactional(readOnly = true)
    public List<RequestDtos.ListItem> list() {
        // 1) 최신순 요청
        final List<Request> requests = requestRepository.findAllByOrderByCreatedAtDesc();

        // 2) 역 코드 배치 조회(N+1 방지)
        final List<String> codes = requests.stream()
                .map(Request::getStationCode)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        final Map<String, Station> stationMap = codes.isEmpty()
                ? Map.of()
                : stationRepository.findAllByCodeIn(codes).stream()
                .collect(Collectors.toMap(Station::getCode, s -> s));

        // 2.5) 합계 배치 조회 (slug 기준)
        final List<String> slugs = requests.stream()
                .map(Request::getSlug)
                .collect(Collectors.toList());

        final Map<String, Integer> totalMap =
                slugs.isEmpty() ? Map.of()
                        : rpaRepository.sumByRequestIds(slugs).stream()
                        .collect(Collectors.toMap(
                                RequestPlaceAggregateRepository.SumRow::getRequestId,
                                row -> row.getTotal() == null ? 0 : row.getTotal().intValue()
                        ));

        // 3) DTO 매핑 (station_code는 응답에서 제외)
        return requests.stream()
                .map(r -> {
                    Station st = stationMap.get(r.getStationCode());
                    String stationName = (st != null) ? st.getName() : null;
                    String stationLine = (st != null) ? st.getLine() : null;
                    int total = totalMap.getOrDefault(r.getSlug(), 0);

                    return new RequestDtos.ListItem(
                            r.getSlug(),          // slug
                            stationName,          // station_name
                            stationLine,          // station_line
                            null,                 // road_address_name (지금은 null)
                            total,                // ✅ 합계
                            r.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    // ===== 내부 유틸 =====

    /**
     * 입력을 DB의 정규코드로 확정한다.
     * - 정규코드(S0701 등)면 그대로.
     * - 레거시(7-733 등)는 간이 매핑 → 정규코드.
     * - JSON 문자열이면 code/name+line로 조회 → 정규코드.
     * - 실패 시 IllegalArgumentException 던져 400 (FK로 500 방지).
     */
    private String resolveStationCodeOr400(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("station_code is required");
        }
        String s = raw.trim();

        // 0) 레거시 별칭 우선 치환
        if (LEGACY_ALIAS.containsKey(s)) {
            return ensureExistsOrThrow(LEGACY_ALIAS.get(s), raw);
        }

        // 1) 정확히 코드로 존재?
        Optional<Station> byExact = stationRepository.findByCode(s);
        if (byExact.isPresent()) return byExact.get().getCode();

        // 2) JSON 문자열인 경우
        if ((s.startsWith("{") && s.endsWith("}")) || s.startsWith("\"{")) {
            String json = trimQuotesIfNeeded(s);

            String codeFromJson = extract(CODE_P, json);
            if (codeFromJson != null) {
                // JSON 안의 code가 레거시면 매핑
                if (LEGACY_ALIAS.containsKey(codeFromJson)) {
                    return ensureExistsOrThrow(LEGACY_ALIAS.get(codeFromJson), raw);
                }
                // 정규코드로 존재하면 그대로
                Optional<Station> byJsonCode = stationRepository.findByCode(codeFromJson);
                if (byJsonCode.isPresent()) return byJsonCode.get().getCode();
            }

            String name = extract(NAME_P, json);
            String line = extract(LINE_P, json);

            if (name != null && line != null) {
                Optional<Station> byNameLine = stationRepository.findByNameAndLine(name, line);
                if (byNameLine.isPresent()) return byNameLine.get().getCode();

                // "7" <-> "7호선" 상호 보완
                for (String cand : lineCandidates(line)) {
                    Optional<Station> tryCand = stationRepository.findByNameAndLine(name, cand);
                    if (tryCand.isPresent()) return tryCand.get().getCode();
                }
            }

            throw new IllegalArgumentException("invalid station_code: " + raw);
        }

        // 3) 레거시 포맷 "7-733" 등
        if (LEGACY_CODE_P.matcher(s).matches()) {
            if (LEGACY_ALIAS.containsKey(s)) {
                return ensureExistsOrThrow(LEGACY_ALIAS.get(s), raw);
            }
            Optional<Station> byLegacyAsCode = stationRepository.findByCode(s);
            if (byLegacyAsCode.isPresent()) return byLegacyAsCode.get().getCode();
            throw new IllegalArgumentException("invalid station_code: " + raw);
        }

        // 4) 그 외 문자열은 무효
        throw new IllegalArgumentException("invalid station_code: " + raw);
    }

    // 주어진 코드가 DB에 있는지 보증, 없으면 400
    private String ensureExistsOrThrow(String code, String raw) {
        Optional<Station> st = stationRepository.findByCode(code);
        if (st.isPresent()) return st.get().getCode();
        throw new IllegalArgumentException("invalid station_code: " + raw);
    }

    private String trimQuotesIfNeeded(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String extract(Pattern p, String src) {
        Matcher m = p.matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private List<String> lineCandidates(String line) {
        if (line == null) return List.of();
        String trimmed = line.trim();
        if (trimmed.matches("^\\d+$")) {           // "7" -> ["7", "7호선"]
            return List.of(trimmed, trimmed + "호선");
        }
        if (trimmed.endsWith("호선")) {            // "7호선" -> ["7호선", "7"]
            String digits = trimmed.replace("호선", "");
            if (digits.matches("^\\d+$")) {
                return List.of(trimmed, digits);
            }
        }
        return List.of(trimmed);
    }
}
