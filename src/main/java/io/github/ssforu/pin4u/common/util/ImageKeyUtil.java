package io.github.ssforu.pin4u.common.util;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class ImageKeyUtil {
    private ImageKeyUtil() {}

    public static String normalizeExt(String filename) {
        if (filename == null) return "jpg";
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".jpeg") || f.endsWith(".jpg")) return "jpg";
        if (f.endsWith(".png")) return "png";
        if (f.endsWith(".webp")) return "webp";
        return "jpg";
    }

    /** 시연용 안전키(개인지도/노트 이미지). slug/gid/filename 비어도 절대 실패하지 않게 */
    public static String buildPublicKey(String publicPrefix, String slug, String gid, String filename) {
        LocalDate d = LocalDate.now();
        String ext = normalizeExt(filename);

        // publicPrefix 정리
        String prefix = (publicPrefix == null || publicPrefix.isBlank())
                ? ""
                : (publicPrefix.endsWith("/") ? publicPrefix : publicPrefix + "/");

        // 빈 값 대체 (이중 슬래시 방지)
        String safeSlug = (slug == null || slug.isBlank()) ? "_" : slug.trim();
        String safeGid  = (gid  == null || gid.isBlank())  ? UUID.randomUUID().toString() : gid.trim();

        return String.format(
                "%srequests/%s/notes/%s/%04d/%02d/%02d/%s.%s",
                prefix, safeSlug, safeGid, d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                UUID.randomUUID(), ext
        );
    }

    /** (옵션) 그룹 프로필 이미지 키 — 필요 시 같은 컨트롤러에서 이 메서드로 분기해서 써도 됨 */
    public static String buildGroupProfileKey(String publicPrefix, String groupSlug, String filename) {
        LocalDate d = LocalDate.now();
        String ext = normalizeExt(filename);

        String prefix = (publicPrefix == null || publicPrefix.isBlank())
                ? ""
                : (publicPrefix.endsWith("/") ? publicPrefix : publicPrefix + "/");

        String safeGroup = (groupSlug == null || groupSlug.isBlank()) ? "_precreate" : groupSlug.trim();

        return String.format(
                "%sgroups/%s/profile/%04d/%02d/%02d/%s.%s",
                prefix, safeGroup, d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                UUID.randomUUID(), ext
        );
    }
}
