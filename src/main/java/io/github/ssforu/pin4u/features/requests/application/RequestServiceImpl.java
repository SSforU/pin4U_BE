package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;

// ✅ member 패키지 구조에 맞게 User 사용
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import io.github.ssforu.pin4u.features.member.domain.User;

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
    private final GroupRepository groupRepository; // ✅ 그룹 조회 의존성
    private final UserRepository userRepository;   // ✅ 오너 닉네임 조회 의존성

    private static final Map<String, String> LEGACY_ALIAS = Map.of(
            "7-733", "S0701"
    );

    private static final Pattern CODE_P = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NAME_P = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern LINE_P = Pattern.compile("\\\"line\\\"\\s*:\\s*\\\"?([0-9A-Za-z가-힣]+)\\\"?");
    private static final Pattern LEGACY_CODE_P = Pattern.compile("^\\d+-\\d+$");

    public RequestServiceImpl(RequestRepository requestRepository,
                              StationRepository stationRepository,
                              SlugGenerator slugGenerator,
                              RequestPlaceAggregateRepository rpaRepository,
                              GroupRepository groupRepository,
                              UserRepository userRepository) {        // ✅ 추가
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.slugGenerator = slugGenerator;
        this.rpaRepository = rpaRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;                       // ✅ 추가
    }

    @Override
    public RequestDtos.CreatedRequestDTO create(Long ownerUserId, String stationCodeRaw, String requestMessage, String groupSlug) {
        if (ownerUserId == null) throw new IllegalArgumentException("login required");

        // 1) 역 코드 정규화
        String normalizedCode = resolveStationCodeOr400(stationCodeRaw);
        String slug = slugGenerator.generate(normalizedCode);

        // 2) 그룹 연결(옵션) + "그룹은 단일 역" 제약
        Long groupId = null;
        if (groupSlug != null && !groupSlug.isBlank()) {
            Group g = groupRepository.findBySlug(groupSlug.trim())
                    .orElseThrow(() -> new IllegalArgumentException("invalid group_slug"));

            // 오너만 그룹 요청 생성 허용
            if (!g.getOwnerUserId().equals(ownerUserId)) {
                throw new IllegalArgumentException("only group owner can create group requests");
            }

            // 이미 그룹에 요청들이 있다면 모두 동일 역이어야 함
            List<Request> existing = requestRepository.findAllByGroupId(g.getId());
            if (!existing.isEmpty()) {
                String existingCode = existing.get(0).getStationCode();
                if (existingCode != null && !existingCode.equals(normalizedCode)) {
                    throw new IllegalArgumentException("group must use a single station");
                }
            }
            groupId = g.getId();        // ✅ 저장 전에 group_id 세팅
        }

        // 3) 저장
        Request saved = requestRepository.save(
                new Request(slug, ownerUserId, normalizedCode, groupId, requestMessage)
        );

        // 4) 응답
        return new RequestDtos.CreatedRequestDTO(
                saved.getSlug(),
                saved.getStationCode(),
                saved.getRequestMessage(),
                saved.getCreatedAt()
        );
    }

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
                r.getSlug(),
                stationName,
                stationLine,
                null,
                total,
                r.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestDtos.ListItem> list() {
        final List<Request> requests = requestRepository.findAllByOrderByCreatedAtDesc();

        final List<String> codes = requests.stream()
                .map(Request::getStationCode)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        final Map<String, Station> stationMap = codes.isEmpty()
                ? Map.of()
                : stationRepository.findAllByCodeIn(codes).stream()
                .collect(Collectors.toMap(Station::getCode, s -> s));

        final List<String> slugs = requests.stream()
                .map(Request::getSlug)
                .toList();

        final Map<String, Integer> totalMap =
                slugs.isEmpty() ? Map.of()
                        : rpaRepository.sumByRequestIds(slugs).stream()
                        .collect(Collectors.toMap(
                                RequestPlaceAggregateRepository.SumRow::getRequestId,
                                row -> row.getTotal() == null ? 0 : row.getTotal().intValue()
                        ));

        return requests.stream()
                .map(r -> {
                    Station st = stationMap.get(r.getStationCode());
                    String stationName = (st != null) ? st.getName() : null;
                    String stationLine = (st != null) ? st.getLine() : null;
                    int total = totalMap.getOrDefault(r.getSlug(), 0);

                    return new RequestDtos.ListItem(
                            r.getSlug(),
                            stationName,
                            stationLine,
                            null,
                            total,
                            r.getCreatedAt()
                    );
                })
                .toList();
    }

    // ✅ 추가: 요청 슬러그로 오너 닉네임 조회 (읽기 전용)
    @Override
    @Transactional(readOnly = true)
    public OwnerBrief getOwnerByRequestSlug(String slug) {
        var req = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("request not found"));
        Long uid = req.getOwnerUserId();
        if (uid == null) throw new IllegalArgumentException("owner not found");
        String nick = userRepository.findById(uid)
                .map(User::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .orElse("사용자"); // 빈 닉네임 대비 안전 디폴트
        return new OwnerBrief(uid, nick);
    }

    // --- 이하 유틸/검증 동일 ---
    private String resolveStationCodeOr400(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("station_code is required");
        }
        String s = raw.trim();

        if (LEGACY_ALIAS.containsKey(s)) {
            return ensureExistsOrThrow(LEGACY_ALIAS.get(s), raw);
        }

        Optional<Station> byExact = stationRepository.findByCode(s);
        if (byExact.isPresent()) return byExact.get().getCode();

        if ((s.startsWith("{") && s.endsWith("}")) || s.startsWith("\"{")) {
            String json = trimQuotesIfNeeded(s);

            String codeFromJson = extract(CODE_P, json);
            if (codeFromJson != null) {
                if (LEGACY_ALIAS.containsKey(codeFromJson)) {
                    return ensureExistsOrThrow(LEGACY_ALIAS.get(codeFromJson), raw);
                }
                Optional<Station> byJsonCode = stationRepository.findByCode(codeFromJson);
                if (byJsonCode.isPresent()) return byJsonCode.get().getCode();
            }

            String name = extract(NAME_P, json);
            String line = extract(LINE_P, json);

            if (name != null && line != null) {
                Optional<Station> byNameLine = stationRepository.findByNameAndLine(name, line);
                if (byNameLine.isPresent()) return byNameLine.get().getCode();

                for (String cand : lineCandidates(line)) {
                    Optional<Station> tryCand = stationRepository.findByNameAndLine(name, cand);
                    if (tryCand.isPresent()) return tryCand.get().getCode();
                }
            }

            throw new IllegalArgumentException("invalid station_code: " + raw);
        }

        if (LEGACY_CODE_P.matcher(s).matches()) {
            if (LEGACY_ALIAS.containsKey(s)) {
                return ensureExistsOrThrow(LEGACY_ALIAS.get(s), raw);
            }
            Optional<Station> byLegacyAsCode = stationRepository.findByCode(s);
            if (byLegacyAsCode.isPresent()) return byLegacyAsCode.get().getCode();
            throw new IllegalArgumentException("invalid station_code: " + raw);
        }

        throw new IllegalArgumentException("invalid station_code: " + raw);
    }

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
        if (trimmed.matches("^\\d+$")) {
            return List.of(trimmed, trimmed + "호선");
        }
        if (trimmed.endsWith("호선")) {
            String digits = trimmed.replace("호선", "");
            if (digits.matches("^\\d+$")) {
                return List.of(trimmed, digits);
            }
        }
        return List.of(trimmed);
    }

    @Override
    public DeleteResult delete(Long me, String slug) {
        var opt = requestRepository.findBySlug(slug);
        if (opt.isEmpty()) return DeleteResult.NOT_FOUND;
        var req = opt.get();
        if (!req.getOwnerUserId().equals(me)) return DeleteResult.NOT_OWNER;
        requestRepository.delete(req);
        return DeleteResult.OK;
    }
}
