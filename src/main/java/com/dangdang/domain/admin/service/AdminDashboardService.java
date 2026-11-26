package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminDashboardResponse;
import com.dangdang.domain.admin.dto.response.AdminDashboardResponse.Day;
import com.dangdang.domain.admin.dto.response.AdminDashboardResponse.Summary;
import com.dangdang.domain.admin.dto.response.AdminDashboardResponse.Todo;
import com.dangdang.domain.admin.enums.DashboardPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dangdang.global.config.ClockConfig.KST;

// 운영자 대시보드 숫자. 여러 표를 세기만 해서 엔티티 대신 SQL로 바로 센다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    // 날짜는 한국 날짜로 묶는다 (밤 11시 기록이 다음 날로 넘어가지 않게)
    private static final String KST_DAY = "(%s AT TIME ZONE 'Asia/Seoul')::date";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminDashboardResponse get(DashboardPeriod period) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KST);
        LocalDate from = today.minusDays(period.getDays() - 1L);
        Timestamp start = ts(from.atStartOfDay(KST).toInstant());
        Timestamp end = ts(today.plusDays(1).atStartOfDay(KST).toInstant());   // 오늘 자정(내일 0시) 전까지

        return new AdminDashboardResponse(now, period, from, today,
                todo(now, end), summary(start, end), daily(from, today, start, end));
    }

    private Todo todo(Instant now, Timestamp endOfToday) {
        Timestamp t = ts(now);
        long pending = count("SELECT count(*) FROM (SELECT DISTINCT target_type, target_id FROM reports WHERE status = 'RECEIVED') r");
        long crisis = count("SELECT count(*) FROM (SELECT DISTINCT target_type, target_id FROM reports"
                + " WHERE status = 'RECEIVED' AND reason = 'CRISIS') r");
        Instant oldest = instant(jdbc.queryForObject("SELECT min(created_at) FROM reports WHERE status = 'RECEIVED'", Timestamp.class));
        long endingToday = count("SELECT count(*) FROM user_sanctions WHERE lifted_at IS NULL AND ends_at > ? AND ends_at < ?", t, endOfToday);
        // 이의 신청은 처리 상태가 따로 없다 — 그 제한이 아직 걸려 있으면 볼 일이 남은 것
        long appeals = count("SELECT count(*) FROM sanction_appeals a JOIN user_sanctions s ON s.id = a.sanction_id"
                + " WHERE s.lifted_at IS NULL AND s.starts_at <= ? AND (s.ends_at IS NULL OR s.ends_at > ?)", t, t);
        long scheduled = count("SELECT count(*) FROM announcements WHERE active_from > ?", t);
        Instant next = instant(jdbc.queryForObject("SELECT min(active_from) FROM announcements WHERE active_from > ?", Timestamp.class, t));
        return new Todo(pending, crisis, oldest, endingToday, appeals, scheduled, next);
    }

    private Summary summary(Timestamp start, Timestamp end) {
        long total = count("SELECT count(*) FROM users WHERE role = 'USER'");
        long active = count("SELECT count(DISTINCT user_id) FROM ("
                + " SELECT user_id FROM readings WHERE deleted_at IS NULL AND measured_at >= ? AND measured_at < ?"
                + " UNION SELECT user_id FROM meals WHERE deleted_at IS NULL AND started_at >= ? AND started_at < ?) a", start, end, start, end);
        long joined = count("SELECT count(*) FROM users WHERE role = 'USER' AND created_at >= ? AND created_at < ?", start, end);
        long readings = count("SELECT count(*) FROM readings WHERE deleted_at IS NULL AND measured_at >= ? AND measured_at < ?", start, end);
        long meals = count("SELECT count(*) FROM meals WHERE deleted_at IS NULL AND started_at >= ? AND started_at < ?", start, end);
        long posts = count("SELECT count(*) FROM posts WHERE deleted_at IS NULL AND created_at >= ? AND created_at < ?", start, end);
        long reports = count("SELECT count(*) FROM reports WHERE created_at >= ? AND created_at < ?", start, end);
        return new Summary(total, active, joined, readings, meals, posts, reports);
    }

    private List<Day> daily(LocalDate from, LocalDate to, Timestamp start, Timestamp end) {
        Map<LocalDate, Long> active = byDay("SELECT d, count(DISTINCT user_id) FROM ("
                + " SELECT " + day("measured_at") + " d, user_id FROM readings WHERE deleted_at IS NULL AND measured_at >= ? AND measured_at < ?"
                + " UNION SELECT " + day("started_at") + " d, user_id FROM meals WHERE deleted_at IS NULL AND started_at >= ? AND started_at < ?) a"
                + " GROUP BY d", start, end, start, end);
        Map<LocalDate, Long> readings = byDay(grouped("readings", "measured_at", "deleted_at IS NULL"), start, end);
        Map<LocalDate, Long> meals = byDay(grouped("meals", "started_at", "deleted_at IS NULL"), start, end);
        Map<LocalDate, Long> posts = byDay(grouped("posts", "created_at", "deleted_at IS NULL"), start, end);
        Map<LocalDate, Long> reports = byDay(grouped("reports", "created_at", "true"), start, end);

        List<Day> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(new Day(d, active.getOrDefault(d, 0L), readings.getOrDefault(d, 0L), meals.getOrDefault(d, 0L),
                    posts.getOrDefault(d, 0L), reports.getOrDefault(d, 0L)));
        }
        return days;
    }

    // 표 이름·칸 이름은 코드에 적힌 값만 들어온다 (사용자 입력 아님)
    private static String grouped(String table, String column, String where) {
        return "SELECT " + day(column) + " d, count(*) FROM " + table
                + " WHERE " + where + " AND " + column + " >= ? AND " + column + " < ? GROUP BY d";
    }

    private static String day(String column) {
        return KST_DAY.formatted(column);
    }

    private Map<LocalDate, Long> byDay(String sql, Object... args) {
        Map<LocalDate, Long> map = new HashMap<>();
        jdbc.query(sql, rs -> {
            map.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
        }, args);
        return map;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
