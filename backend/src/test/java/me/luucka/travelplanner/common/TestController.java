package me.luucka.travelplanner.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.luucka.travelplanner.common.storage.UnsupportedFileTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Controller
class TestController {

    record Body(@NotBlank String name) {}

    @PostMapping("/test/validate")
    @ResponseBody
    String validate(@Valid @RequestBody Body body) {
        return body.name();
    }

    @GetMapping("/test/not-found")
    void notFound() {
        throw new NotFoundException();
    }

    @GetMapping("/test/api")
    void api() {
        throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verify your email first");
    }

    @GetMapping("/test/too-large")
    void tooLarge() {
        throw new MaxUploadSizeExceededException(25L * 1024 * 1024);
    }

    @GetMapping("/test/unsupported")
    void unsupported() {
        throw new UnsupportedFileTypeException();
    }
}
