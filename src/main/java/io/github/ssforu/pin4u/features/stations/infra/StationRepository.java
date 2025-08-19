package io.github.ssforu.pin4u.features.stations.infra;

import io.github.ssforu.pin4u.features.stations.domain.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StationRepository extends JpaRepository<Station, String> {

    // 단어 포함 검색 (공백 포함 입력 대비): name ILIKE %q%
    @Query("""
        select s from Station s
        where lower(s.name) like lower(concat('%', :q, '%'))
        order by s.name asc
    """)
    Page<Station> searchByNameContains(@Param("q") String q, Pageable pageable);
}
