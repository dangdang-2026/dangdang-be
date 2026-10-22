package com.dangdang.global.exception.model;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {

    String getCode(); // 예: AUTH_40901

    String getMessage(); // 화면에 보여 줄 문장

    HttpStatus getStatus(); // 예: 409 CONFLICT
}