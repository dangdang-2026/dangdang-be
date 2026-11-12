package com.dangdang.domain.caresite.entity;

import com.dangdang.domain.caresite.enums.Department;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// care_sites 테이블 한 줄 = 사용자가 다니는 병원(진료과) 하나
@Getter
@Entity
@Table(name = "care_sites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;                     // 병원 이름

    @Enumerated(EnumType.STRING)             // DB에 "OBGYN" 같은 글자로 저장
    @Column(nullable = false, length = 15)
    private Department department;

    @Column(length = 100)
    private String hiraCode;                 // 심평원 암호화 요양기호 (검색해서 고른 경우만, 없어도 됨)

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 30)
    private String roleNote;                 // "혈당 관리 담당" 같은 짧은 메모

    @Column(name = "is_primary", nullable = false)
    private boolean primary;                 // 대표 병원인지 (사용자당 하나만)

    @Column(nullable = false)
    private int sortOrder;                   // 목록 순서 (작을수록 위)

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 새 병원 만들기
    public static CareSite of(User user, String name, Department department, String hiraCode,
                              String address, String phone, String roleNote, boolean primary, int sortOrder) {
        CareSite c = new CareSite();
        c.user = user;
        c.name = name;
        c.department = department;
        c.hiraCode = hiraCode;
        c.address = address;
        c.phone = phone;
        c.roleNote = roleNote;
        c.primary = primary;
        c.sortOrder = sortOrder;
        return c;
    }

    // 수정. null로 온 칸은 그대로 둠 (대표 지정은 markPrimary·unmarkPrimary로 따로 함)
    public void update(String name, Department department, String hiraCode,
                       String address, String phone, String roleNote, Integer sortOrder) {
        if (name != null) this.name = name;
        if (department != null) this.department = department;
        if (hiraCode != null) this.hiraCode = hiraCode;
        if (address != null) this.address = address;
        if (phone != null) this.phone = phone;
        if (roleNote != null) this.roleNote = roleNote;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    // 대표 병원으로 지정
    public void markPrimary() {
        this.primary = true;
    }

    // 대표 병원 해제 (다른 병원을 대표로 바꿀 때)
    public void unmarkPrimary() {
        this.primary = false;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠
    // 대표 병원을 지우면 대표도 같이 풀림
    public void delete(Instant now) {
        this.deletedAt = now;
        this.primary = false;
    }
}
