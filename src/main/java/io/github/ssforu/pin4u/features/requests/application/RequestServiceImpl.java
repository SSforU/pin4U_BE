// src/main/java/io/github/ssforu/pin4u/features/requests/application/RequestServiceImpl.java
package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final SlugGenerator slugGenerator;

    // JSON 문자열에서 단순 추출용 패턴
    private static final Pattern CODE_P = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NAME_P = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern LINE_P = Pattern.compile("\\\"line\\\"\\s*:\\s*\\\"?([0-9A-Za-z가-힣]+)\\\"?");

    // 레거시 코드(숫자-숫자), 예: 7-733, 111-1
    private static final Pattern LEGACY_CODE_P = Pattern.compile("^\\d+-\\d+$");

    public RequestServiceImpl(RequestRepository requestRepository,
                              StationRepository stationRepository,
                              SlugGenerator slugGenerator) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.slugGenerator = slugGenerator;
    }

    @Override
    public RequestDtos.CreatedRequestDTO create(String ownerNickname, String stationCodeRaw, String requestMessage) {

        // ✅ 입력을 '저장에 쓸 코드'로 정규화하되, 못 찾으면 그대로 저장(400 금지)
        String normalizedCode = resolveStationCodeLenient(stationCodeRaw);

        // slug seed는 정규화된 코드 사용(정규코드면 그걸, 레거시면 레거시 그대로)
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

    /**
     * 가능한 경우 DB의 정규코드로 매핑.
     * - 정규코드(S0701 등) -> 그대로 저장
     * - JSON 문자열 -> name/line 또는 code로 DB 조회 성공 시 정규코드로 저장
     * - 레거시(7-733, 111-1 등) -> DB에 동일 code가 없더라도 '그대로 저장' (여기서 400 안 냄)
     * - 그 외 알 수 없는 문자열 -> DB에 없더라도 '그대로 저장'
     */
    private String resolveStationCodeLenient(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            // 완전 공백만 400
            throw new IllegalArgumentException("station_code is required");
        }
        String s = raw.trim();

        // 1) 정규코드/기타 문자열: DB에 있으면 그 코드(정규코드일 가능성 큼)
        Optional<Station> byExact = stationRepository.findByCode(s);
        if (byExact.isPresent()) {
            return byExact.get().getCode();
        }

        // 2) JSON 문자열로 온 경우: code, name+line 순서로 매핑 시도
        if ((s.startsWith("{") && s.endsWith("}")) || s.startsWith("\"{")) {
            String json = trimQuotesIfNeeded(s);

            String codeFromJson = extract(CODE_P, json);
            if (codeFromJson != null) {
                Optional<Station> byJsonCode = stationRepository.findByCode(codeFromJson);
                if (byJsonCode.isPresent()) return byJsonCode.get().getCode();
            }

            String name = extract(NAME_P, json);
            String line = extract(LINE_P, json);

            if (name != null && line != null) {
                // name + line으로 조회 (StationRepository에 해당 메서드가 이미 존재해야 함)
                Optional<Station> byNameLine = stationRepository.findByNameAndLine(name, line);
                if (byNameLine.isPresent()) return byNameLine.get().getCode();

                // "7" <-> "7호선" 상호 보완
                for (String cand : lineCandidates(line)) {
                    Optional<Station> tryCand = stationRepository.findByNameAndLine(name, cand);
                    if (tryCand.isPresent()) return tryCand.get().getCode();
                }
            }

            // JSON에서 못 찾으면: code가 있었다면 그걸, 아니면 원문 그대로 저장
            if (codeFromJson != null && !codeFromJson.isBlank()) return codeFromJson;
            return s;
        }

        // 3) 레거시(숫자-숫자) 포맷: DB에 없더라도 그대로 저장 (여기서 더 이상 400 안 냄)
        if (LEGACY_CODE_P.matcher(s).matches()) {
            Optional<Station> byLegacyAsCode = stationRepository.findByCode(s);
            if (byLegacyAsCode.isPresent()) return byLegacyAsCode.get().getCode();
            return s; // ✅ 그대로 저장
        }

        // 4) 그 외: DB에 없더라도 그대로 저장
        return s;
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

    @Override
    public List<RequestDtos.ListItem> list() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(r -> new RequestDtos.ListItem(
                        r.getSlug(),
                        r.getOwnerNickname(),
                        r.getStationCode(),
                        r.getRequestMessage(),
                        r.getRecommendCount(),
                        r.getCreatedAt()
                ))
                .toList();
    }
}
