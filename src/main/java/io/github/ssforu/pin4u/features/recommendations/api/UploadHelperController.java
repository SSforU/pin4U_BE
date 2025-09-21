package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.config.UploadProps;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.common.util.ImageKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

record MakeKeyRequest(@NotBlank String slug, @NotBlank String filename) {}
record MakeKeyResponse(String key, String public_url) {}

@RestController
public class UploadHelperController {
    private final UploadProps props;
    public UploadHelperController(UploadProps props) { this.props = props; }

    @PostMapping("/api/uploads/images/make-key")
    public ApiResponse<MakeKeyResponse> makeKey(@RequestBody MakeKeyRequest req, HttpServletRequest r) {
        String gid = WebUtils.getCookie(r, "gid") != null ? WebUtils.getCookie(r, "gid").getValue() : "anonymous";
        String key = ImageKeyUtil.buildPublicKey(props.getPublicPrefix(), req.slug(), gid, req.filename());
        String url = props.toPublicUrl(key);
        return ApiResponse.success(new MakeKeyResponse(key, url));
    }
}
