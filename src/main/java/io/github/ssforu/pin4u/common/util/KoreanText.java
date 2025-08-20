package io.github.ssforu.pin4u.common.util;

public final class KoreanText {
    private KoreanText() {}

    /** 공백 압축 + 앞뒤 공백 제거 + 소문자화 */
    public static String normalize(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
