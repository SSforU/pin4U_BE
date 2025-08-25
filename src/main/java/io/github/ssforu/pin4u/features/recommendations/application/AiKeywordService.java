package io.github.ssforu.pin4u.features.recommendations.application;

import java.util.List;

public interface AiKeywordService {
    /**
     * 한국어 문장(request_message)에서 장소 검색에 쓸 키워드를 최대 2개 추출.
     * 실패 시 간단 휴리스틱 폴백을 돌려준다.
     */
    List<String> extractTop2(String message);
}
