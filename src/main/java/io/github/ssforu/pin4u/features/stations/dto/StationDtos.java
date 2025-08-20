// src/main/java/io/github/ssforu/pin4u/features/stations/dto/StationDtos.java
package io.github.ssforu.pin4u.features.stations.dto;

import java.math.BigDecimal;
import java.util.List;

public final class StationDtos {

    public record StationItem(
            String code,
            String name,
            String line,
            BigDecimal lat,
            BigDecimal lng
    ) {}

    public record SearchResponse(
            List<StationItem> items,
            int count
    ) {}
}
