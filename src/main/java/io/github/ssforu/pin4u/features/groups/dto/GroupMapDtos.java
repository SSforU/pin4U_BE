package io.github.ssforu.pin4u.features.groups.dto;

import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;

import java.math.BigDecimal;
import java.util.List;

public final class GroupMapDtos {
    private GroupMapDtos() {}

    public record GroupBrief(Long id, String slug, String name, String image_url) {}

    public record StationCenter(BigDecimal lat, BigDecimal lng) {}

    public record Response(
            GroupBrief group,
            StationCenter station_center,
            List<RequestDetailDtos.Item> items
    ) {}
}
