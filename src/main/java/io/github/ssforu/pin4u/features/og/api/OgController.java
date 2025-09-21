// src/main/java/io/github/ssforu/pin4u/features/og/api/OgController.java
package io.github.ssforu.pin4u.features.og.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class OgController {
    @GetMapping("/r/{slug}")
    public String og(@PathVariable String slug,
                     @RequestParam(required = false) String title,
                     Model model) {
        model.addAttribute("title", title != null ? title : "pin4u");
        model.addAttribute("desc", "친구가 공유한 추천 지도를 확인해보세요");
        return "og"; // 템플릿 이름
    }
}
