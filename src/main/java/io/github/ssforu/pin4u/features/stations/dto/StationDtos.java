package io.github.ssforu.pin4u.features.stations.dto;

import java.util.List;

public class StationDtos {

    public record StationItem(String code, String name, String line, double lat, double lng) {}

    public record SearchResponse(List<StationItem> items, int count) {}
}
