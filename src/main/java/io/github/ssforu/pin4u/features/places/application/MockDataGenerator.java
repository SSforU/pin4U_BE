package io.github.ssforu.pin4u.features.places.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

@Component
public class MockDataGenerator {

    private static final List<String> PHRASES = List.of(
            "아늑하고 편안한 분위기예요.",
            "활기차고 생동감 넘치는 곳이에요.",
            "직원분들이 정말 친절하세요.",
            "요청사항에 빠르게 응대해 주셔서 좋았어요.",
            "설명을 꼼꼼하고 자세하게 해주셔서 이해하기 쉬웠어요.",
            "창밖으로 보이는 뷰가 정말 멋져요.",
            "매장이 전체적으로 아주 깔끔하고 쾌적해요.",
            "화장실이 깨끗하게 잘 관리되고 있어요.",
            "지하철역/버스정류장에서 가까워서 찾아가기 쉬워요.",
            "주차 공간이 넉넉해서 차 가져가기 편해요.",
            "가격이 합리적이라고 생각해요.",
            "가격대는 조금 있지만 그만큼의 가치를 해요.",
            "전체적으로 만족스러운 경험이었어요.",
            "다음에 꼭 다시 방문하고 싶어요.",
            "근처에 온다면 한번 들러볼 만한 곳이에요."
    );

    private static final List<String> OPENINGS = List.of(
            "오픈 07:00", "오픈 08:00", "오픈 09:00", "오픈 10:00"
    );

    private final ObjectMapper om;

    public MockDataGenerator(ObjectMapper om) {
        this.om = om;
    }

    private Random seeded(String key) {
        long seed = (key == null) ? 0L : key.hashCode();
        return new Random(seed);
    }

    /** 3.0 ~ 5.0, 0.1 step */
    public BigDecimal randomRating(String externalId) {
        Random r = seeded(externalId);
        int step = 30 + r.nextInt(21); // 30..50
        return BigDecimal.valueOf(step / 10.0).setScale(1, RoundingMode.HALF_UP);
    }

    /** 5 ~ 200 */
    public int randomRatingCount(String externalId) {
        Random r = seeded(externalId + "#cnt");
        return 5 + r.nextInt(196);
    }

    /** ["문구"] 형태 JSON */
    public String randomReviewSnippetJson(String externalId) {
        try {
            Random r = seeded(externalId + "#rv");
            String pick = PHRASES.get(r.nextInt(PHRASES.size()));
            return om.writeValueAsString(List.of(pick));
        } catch (Exception e) {
            return null;
        }
    }

    /** ["오픈 HH:MM"] 형태 JSON */
    public String randomOpeningHoursJson(String externalId) {
        try {
            Random r = seeded(externalId + "#op");
            String pick = OPENINGS.get(r.nextInt(OPENINGS.size()));
            return om.writeValueAsString(List.of(pick));
        } catch (Exception e) {
            return null;
        }
    }
}
