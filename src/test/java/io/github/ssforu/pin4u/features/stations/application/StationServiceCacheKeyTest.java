package io.github.ssforu.pin4u.features.stations.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StationServiceImpl.normalizeKey의 캐시 키 정규화 단위 테스트.
 * "강남"과 " 강남 "이 같은 캐시 키를 생성하는지 검증.
 */
class StationServiceCacheKeyTest {

    @Test
    void normalizeKey_trims() {
        assertThat(StationServiceImpl.normalizeKey(" 강남 ")).isEqualTo("강남");
    }

    @Test
    void normalizeKey_lowercases() {
        assertThat(StationServiceImpl.normalizeKey("Gangnam")).isEqualTo("gangnam");
    }

    @Test
    void normalizeKey_trimAndLowercase() {
        assertThat(StationServiceImpl.normalizeKey("  강남역  ")).isEqualTo("강남역");
    }

    @Test
    void normalizeKey_sameQuery_sameKey() {
        String key1 = StationServiceImpl.normalizeKey("강남");
        String key2 = StationServiceImpl.normalizeKey(" 강남 ");
        String key3 = StationServiceImpl.normalizeKey("  강남");
        assertThat(key1).isEqualTo(key2).isEqualTo(key3);
    }

    @Test
    void normalizeKey_null_returnsEmpty() {
        assertThat(StationServiceImpl.normalizeKey(null)).isEqualTo("");
    }
}
