package com.myrecipe.global.exception;

import lombok.Getter;

// 업무 예외는 이 클래스 하나로 던진다. 도메인마다 예외 클래스를 늘리지 않는다.
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
