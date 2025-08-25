package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.common.exception.ApiErrorCode;
import io.github.ssforu.pin4u.common.exception.ApiException;
import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * KakaoSearchPort 의 안전한 대체 구현.
 * 실제 kakaoWebClient/Adapter 가 없을 때만 등록된다.
 */
@Component
@ConditionalOnMissingBean(KakaoSearchPort.class)
public class NoopKakaoSearchAdapter implements KakaoSearchPort {

    @Override
    public List<KakaoPayload.Document> keywordSearch(
            BigDecimal lat, BigDecimal lng, String query, int radiusM, int size
    ) {
        // 필요 시 여기서 빈 리스트 반환으로 완화 가능. 현재는 기존 로직과 동일하게 예외.
        throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "kakao disabled", null);
    }
}
