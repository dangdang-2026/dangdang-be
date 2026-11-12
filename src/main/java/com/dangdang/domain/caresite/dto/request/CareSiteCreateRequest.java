package com.dangdang.domain.caresite.dto.request;

import com.dangdang.domain.caresite.enums.Department;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 병원 등록 요청
public record CareSiteCreateRequest(
        @NotBlank(message = "병원 이름을 입력해 주세요.")
        @Size(max = 50, message = "병원 이름은 50자까지 쓸 수 있어요.")
        String name,

        @NotNull(message = "진료과를 골라 주세요.")
        Department department,

        @Size(max = 100, message = "요양기호가 너무 길어요.")
        String hiraCode,             // 심평원 검색으로 고른 경우만 (없어도 됨)

        @Size(max = 200, message = "주소는 200자까지 쓸 수 있어요.")
        String address,

        @Size(max = 20, message = "전화번호는 20자까지 쓸 수 있어요.")
        String phone,

        @Size(max = 30, message = "메모는 30자까지 쓸 수 있어요.")
        String roleNote,

        Boolean primary,             // 안 보내면 false. true면 원래 대표 병원은 자동으로 풀림

        @PositiveOrZero(message = "정렬 순서는 0 이상이어야 해요.")
        Integer sortOrder) {         // 안 보내면 맨 뒤

    // 안 보낸 경우 false로 봄
    public boolean isPrimary() {
        return Boolean.TRUE.equals(primary);
    }
}
