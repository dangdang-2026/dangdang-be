package com.dangdang.domain.caresite.dto.request;

import com.dangdang.domain.caresite.enums.Department;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 병원 수정 요청 (PATCH) — 보낸 칸만 바뀜, 안 보낸 칸(null)은 그대로 둠
// 주소·전화·메모·요양기호를 비우려면 "" 로 보냄
public record CareSiteUpdateRequest(
        @Size(max = 50, message = "병원 이름은 50자까지 쓸 수 있어요.")
        @Pattern(regexp = ".*\\S.*", message = "병원 이름을 입력해 주세요.")   // 보냈다면 빈 이름은 안 됨
        String name,

        Department department,

        @Size(max = 100, message = "요양기호가 너무 길어요.")
        String hiraCode,

        @Size(max = 200, message = "주소는 200자까지 쓸 수 있어요.")
        String address,

        @Size(max = 20, message = "전화번호는 20자까지 쓸 수 있어요.")
        String phone,

        @Size(max = 30, message = "메모는 30자까지 쓸 수 있어요.")
        String roleNote,

        Boolean primary,             // true = 대표로 지정 (원래 대표는 풀림), false = 대표 해제

        @PositiveOrZero(message = "정렬 순서는 0 이상이어야 해요.")
        Integer sortOrder) {

    // 바꿀 값이 하나도 없는 요청인지
    public boolean isEmpty() {
        return name == null && department == null && hiraCode == null && address == null
                && phone == null && roleNote == null && primary == null && sortOrder == null;
    }
}
