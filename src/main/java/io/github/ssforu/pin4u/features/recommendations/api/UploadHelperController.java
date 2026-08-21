package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.config.UploadProps;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.common.util.ImageKeyUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

record MakeKeyRequest(String slug, String filename) {}
record MakeKeyResponse(String key, String public_url) {}

@RestController
public class UploadHelperController {
    private final UploadProps props;
    public UploadHelperController(UploadProps props) { this.props = props; }

    @SecurityRequirement(name = "uidCookie")
    @PostMapping("/api/uploads/images/make-key")
    public ApiResponse<MakeKeyResponse> makeKey(
            @LoginUser(required = true) Long me,
            @RequestBody MakeKeyRequest req,
            HttpServletRequest r) {

        var gidCookie = WebUtils.getCookie(r, "gid");
        String gid = (gidCookie == null || gidCookie.getValue() == null || gidCookie.getValue().isBlank())
                ? "anonymous"
                : gidCookie.getValue();

        String key = ImageKeyUtil.buildPublicKey(
                props.getPublicPrefix(),
                req.slug(),
                gid,
                req.filename());

        String url = props.toPublicUrl(key);
        return ApiResponse.success(new MakeKeyResponse(key, url));
    }
}
