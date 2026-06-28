package com.jwt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerMediaTest {
    @Test
    void multipartLimitFailureUsesPayloadTooLargeApiResponse() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(60L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
