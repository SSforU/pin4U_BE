package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.config.UploadProps;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.common.util.ImageKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

record MakeKeyRequest(String slug, String filename) {}   // ⬅️ @NotBlank 제거
record MakeKeyResponse(String key, String public_url) {}

@RestController
public class UploadHelperController {
    private final UploadProps props;
    public UploadHelperController(UploadProps props) { this.props = props; }

    @PostMapping("/api/uploads/images/make-key")
    public ApiResponse<MakeKeyResponse> makeKey(@RequestBody MakeKeyRequest req, HttpServletRequest r) {
        // gid 쿠키 없으면 임의/anonymous로
        var gidCookie = WebUtils.getCookie(r, "gid");
        String gid = (gidCookie == null || gidCookie.getValue() == null || gidCookie.getValue().isBlank())
                ? "anonymous"
                : gidCookie.getValue();

        // slug/filename이 비어도 ImageKeyUtil이 안전하게 처리함
        String key = ImageKeyUtil.buildPublicKey(
                props.getPublicPrefix(),
                req.slug(),
                gid,
                req.filename()
        );

        String url = props.toPublicUrl(key);
        return ApiResponse.success(new MakeKeyResponse(key, url));
    }
}
