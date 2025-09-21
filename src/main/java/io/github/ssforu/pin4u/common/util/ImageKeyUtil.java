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

    public static String buildPublicKey(String publicPrefix, String slug, String gid, String filename) {
        LocalDate d = LocalDate.now();
        String ext = normalizeExt(filename);
        String prefix = (publicPrefix == null || publicPrefix.isBlank())
                ? "" : (publicPrefix.endsWith("/") ? publicPrefix : publicPrefix + "/");
        return String.format("%srequests/%s/notes/%s/%04d/%02d/%02d/%s.%s",
                prefix, slug, gid, d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                UUID.randomUUID(), ext);
    }
}
