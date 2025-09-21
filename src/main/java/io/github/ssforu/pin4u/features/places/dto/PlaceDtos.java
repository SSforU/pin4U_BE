package io.github.ssforu.pin4u.features.places.dto;

import java.math.BigDecimal;
import java.util.List;

public final class PlaceDtos {

    public record StationBrief(
            String code, String name, String line,
            BigDecimal lat, BigDecimal lng
    ) {}

    public record MockDto(
            Double rating,
            Integer rating_count,
            List<String> review_snippets,
            List<String> image_urls,
            List<String> opening_hours
    ) {}

    public record Item(
            String external_id,
            String id,
            String place_name,
            String category_group_code,
            String category_group_name,
            String category_name,
            String phone,
            String address_name,
            String road_address_name,
            String x,
            String y,
            String place_url,
            Integer distance_m,
            MockDto mock
    ) {}

    public record SearchResponse(
            StationBrief station,
            List<Item> items
    ) {}
}
