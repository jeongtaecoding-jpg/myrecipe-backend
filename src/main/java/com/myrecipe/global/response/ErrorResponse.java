package com.myrecipe.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myrecipe.global.exception.ErrorCode;
import java.util.List;

// API_SPEC.md "에러 응답" 형식. fieldErrors는 입력값 검증 실패(400)일 때만 들어간다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
