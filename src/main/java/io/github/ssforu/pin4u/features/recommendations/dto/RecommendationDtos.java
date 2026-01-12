// src/main/java/io/github/ssforu/pin4u/features/recommendations/dto/RecommendationDtos.java
package io.github.ssforu.pin4u.features.recommendations.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.*;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public static class SubmitRequest {
        private List<SubmitItem> items;
        public SubmitRequest() {}
        public SubmitRequest(List<SubmitItem> items) { this.items = items; }
        public List<SubmitItem> getItems() { return items; }
        public void setItems(List<SubmitItem> items) { this.items = items; }
    }

    public static class SubmitItem {
        @JsonProperty("external_id")
        @JsonAlias({"externalId", "place_external_id", "placeExternalId"}) // ⬅️ 추가
        private String externalId;

        @JsonProperty("recommender_nickname")  @JsonAlias("recommenderNickname")
        private String recommenderNickname;

        @JsonProperty("recommend_message")     @JsonAlias("recommendMessage")
        private String recommendMessage;

        @JsonProperty("image_url")             @JsonAlias("imageUrl")
        private String imageUrl;

        // 기본값: 공개(true)
        @JsonProperty("image_is_public")       @JsonAlias("imageIsPublic")
        private Boolean imageIsPublic = Boolean.TRUE;

        @JsonProperty("tags")
        private List<String> tags;

        @JsonProperty("guest_id")              @JsonAlias("guestId")
        private String guestId;

        public SubmitItem() {}
        public SubmitItem(String externalId, String recommenderNickname, String recommendMessage,
                          String imageUrl, Boolean imageIsPublic, List<String> tags, String guestId) {
            this.externalId = externalId; this.recommenderNickname = recommenderNickname;
            this.recommendMessage = recommendMessage; this.imageUrl = imageUrl;
            this.imageIsPublic = imageIsPublic;
            this.tags = tags; this.guestId = guestId;
        }
        public String getExternalId() { return externalId; }
        public String getRecommenderNickname() { return recommenderNickname; }
        public String getRecommendMessage() { return recommendMessage; }
        public String getImageUrl() { return imageUrl; }
        public Boolean getImageIsPublic() { return imageIsPublic; }
        public List<String> getTags() { return tags; }
        public String getGuestId() { return guestId; }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SubmitResponse {
        private List<SavedItem> saved = new ArrayList<>();
        private List<SimpleItem> conflicts = new ArrayList<>();
        private List<OutOfRadiusItem> outOfRadius = new ArrayList<>();
        private List<SimpleItem> notFound = new ArrayList<>();
        private List<InvalidItem> invalid = new ArrayList<>();
        private Map<String, Integer> totals = new LinkedHashMap<>();

        public List<SavedItem> getSaved() { return saved; }
        public List<SimpleItem> getConflicts() { return conflicts; }
        public List<OutOfRadiusItem> getOutOfRadius() { return outOfRadius; }
        public List<SimpleItem> getNotFound() { return notFound; }
        public List<InvalidItem> getInvalid() { return invalid; }
        public Map<String, Integer> getTotals() { return totals; }
    }

    public static class SavedItem {
        @JsonProperty("external_id") private String externalId;
        @JsonProperty("recommended_count") private int recommendedCount;
        public SavedItem() {}
        public SavedItem(String externalId, int recommendedCount) {
            this.externalId = externalId; this.recommendedCount = recommendedCount;
        }
        public String getExternalId() { return externalId; }
        public int getRecommendedCount() { return recommendedCount; }
    }

    public static class SimpleItem {
        @JsonProperty("external_id") private String externalId;
        public SimpleItem() {}
        public SimpleItem(String externalId) { this.externalId = externalId; }
        public String getExternalId() { return externalId; }
    }

    public static class OutOfRadiusItem {
        @JsonProperty("external_id") private String externalId;
        @JsonProperty("distance_m") private Integer distanceM;
        public OutOfRadiusItem() {}
        public OutOfRadiusItem(String externalId, Integer distanceM) {
            this.externalId = externalId; this.distanceM = distanceM;
        }
        public String getExternalId() { return externalId; }
        public Integer getDistanceM() { return distanceM; }
    }

    public static class InvalidItem {
        @JsonProperty("external_id") private String externalId;
        private Map<String, String> details = new LinkedHashMap<>();
        public InvalidItem() {}
        public InvalidItem(String externalId, Map<String, String> details) {
            this.externalId = externalId; this.details = details;
        }
        public String getExternalId() { return externalId; }
        public Map<String, String> getDetails() { return details; }
    }
}
