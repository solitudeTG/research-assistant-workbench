package com.researchassistant.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingUploadController())
            .setControllerAdvice(new UploadExceptionHandler())
            .build();

    @Test
    void maxUploadSizeExceededReturnsJsonErrorPayload() throws Exception {
        mockMvc.perform(post("/test/upload").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Uploaded file exceeds the 25 MB limit."));
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingUploadController {

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void upload() {
            throw new MaxUploadSizeExceededException(1_500_000);
        }
    }
}
