package com.dangdang.domain.caresite.dto.response;

import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.enums.Department;

import java.time.Instant;

// 병원 하나 응답
public record CareSiteResponse(
        Long careSiteId,
        String name,
        Department department,
        String hiraCode,
        String address,
        String phone,
        String roleNote,
        boolean primary,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    // 엔티티 -> 응답
    public static CareSiteResponse from(CareSite c) {
        return new CareSiteResponse(
                c.getId(), c.getName(), c.getDepartment(), c.getHiraCode(), c.getAddress(), c.getPhone(),
                c.getRoleNote(), c.isPrimary(), c.getSortOrder(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
