package com.researchassistant.knowledge;

import com.researchassistant.candidates.KnowledgeCandidateController;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        KnowledgeBoardController.class,
        KnowledgeCandidateController.class
})
public class F008ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", "invalid_f008_request",
                        "message", exception.getMessage(),
                        "retryable", false
                ));
    }
}
