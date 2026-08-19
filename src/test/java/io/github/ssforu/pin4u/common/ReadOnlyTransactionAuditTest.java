package io.github.ssforu.pin4u.common;

import io.github.ssforu.pin4u.features.groups.application.GroupMapService;
import io.github.ssforu.pin4u.features.home.application.HomeService;
import io.github.ssforu.pin4u.features.requests.application.RequestDetailServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * readOnly=true 메서드가 실제로 readOnly로 선언되어 있는지 반사적으로 검증.
 * 이슈 5(#36) 조치 이후 회귀 방지.
 */
class ReadOnlyTransactionAuditTest {

    @Test
    void requestDetailServiceImpl_classLevel_isReadOnly() {
        Transactional tx = RequestDetailServiceImpl.class.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

    @Test
    void homeService_dashboard_isReadOnly() throws Exception {
        Transactional tx = HomeService.class.getMethod("dashboard", Long.class).getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

    @Test
    void groupMapService_getGroupMap_isReadOnly() throws Exception {
        Transactional tx = GroupMapService.class
                .getMethod("getGroupMapAsRequestDetail", String.class, Long.class, Integer.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }
}
