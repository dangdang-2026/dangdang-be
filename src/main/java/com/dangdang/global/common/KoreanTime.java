package com.dangdang.global.common;

import com.dangdang.global.config.ClockConfig;

import java.time.Instant;
import java.time.LocalDate;

// 한국 날짜 <-> 세계 공통 시각(Instant) 바꾸기. 하루 경계는 항상 한국 시각 00:00
// DB에는 세계 공통 시각으로 저장되니까, "한국 날짜로 며칠"을 조회할 땐 여기서 바꿔서 씀
public final class KoreanTime {

    private KoreanTime() {
    }

    // 세계 공통 시각 -> 한국 날짜 (한국 새벽 1시는 UTC로 전날이라서 그냥 자르면 안 됨)
    public static LocalDate dateOf(Instant at) {
        return LocalDate.ofInstant(at, ClockConfig.KST);
    }

    // 한국 날짜 -> 그날 0시(한국)의 세계 공통 시각
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ClockConfig.KST).toInstant();
    }

    // 한국 날짜 -> 다음 날 0시(한국)의 세계 공통 시각. 그날을 "start 이상 end 미만"으로 조회할 때 end로 씀
    public static Instant endOfDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }
}
