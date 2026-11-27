package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.ProductCardListResponse;
import com.dangdang.domain.shop.dto.response.ProductRecommendationResponse;
import com.dangdang.domain.shop.dto.response.ProductRecommendationResponse.Reason;
import com.dangdang.domain.shop.dto.response.ProductReviewPageResponse;
import com.dangdang.domain.shop.dto.response.ProductReviewSummary;
import com.dangdang.domain.shop.dto.response.ProductSummaryResponse;
import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.enums.ProductCategory;
import com.dangdang.domain.shop.enums.ProductReviewFilter;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductLikeRepository;
import com.dangdang.domain.shop.repository.ProductRepository;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.global.common.TimeCursor;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 상점 혈당 후기 (be #192). 상점 상품을 고른 식사에 붙은 식후 혈당을, PRODUCT_REVIEW 동의를 켠 사람 것만 이름 없이 센다
// - 한 사람 한 표: 요약은 사람 수. 한 사람이 여러 번 먹었으면 가장 최근 식후 혈당 하나로
// - 목표 안: 잴 때의 그 사람 병원 식후 목표 「미만」 (ReadingEvaluator.aboveTarget 과 같은 규칙). 목표가 빈 기준의 혈당은 뺀다
// - 5명보다 적으면 숫자를 안 준다 (ProductReviewSummary.MIN_PEOPLE)
// 건강정보를 여러 사람 것으로 묶어 읽어서 JPA 대신 SQL로 바로 읽는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReviewQueryService {

    static final int RECOMMEND_DAYS = 14;
    private static final double IN_TARGET_RATE = 0.7;               // REVIEWED_IN_TARGET 으로 볼 목표 안 비율
    private static final BigDecimal LOW_SUGAR_G = BigDecimal.valueOf(5);
    private static final BigDecimal LOW_CARB_G = BigDecimal.valueOf(15);
    private static final List<ProductCategory> SNACKS = List.of(ProductCategory.SNACK, ProductCategory.DRINK);

    // PRODUCT_REVIEW 동의의 가장 최근 줄이 「켬」이고 지금 문서 버전인 사람 (ConsentEvent.isCurrentGrant 와 같은 기준). ?는 문서 버전
    private static final String CONSENTED = """
            SELECT ce.user_id FROM (
                SELECT DISTINCT ON (user_id) user_id, granted, document_version
                FROM consent_events WHERE consent_type = 'PRODUCT_REVIEW'
                ORDER BY user_id, occurred_at DESC, id DESC) ce
            WHERE ce.granted AND ce.document_version = ?""";

    // 후기 한 줄 = (식후 혈당, 상품). 같은 식사에 같은 상품을 두 번 적어도 한 줄. %s 는 상품 조건
    private static final String REVIEWS = """
            SELECT DISTINCT ON (r.id, mi.product_id)
                   r.id, r.user_id, mi.product_id, r.value, c.post_meal_target AS target,
                   c.post_meal_minutes AS minutes, r.measured_at, r.meal_id
            FROM meal_items mi
            JOIN meals m ON m.id = mi.meal_id AND m.deleted_at IS NULL
            JOIN readings r ON r.meal_id = m.id AND r.reading_type = 'POST_MEAL' AND r.deleted_at IS NULL
            JOIN criteria_versions c ON c.id = r.criteria_version_id AND c.post_meal_target IS NOT NULL
            WHERE mi.deleted_at IS NULL AND %s
              AND r.user_id IN (""" + CONSENTED + ")";

    // 그 사람의 지금(가장 최근) 임신 정보
    private static final String LATEST_PREGNANCY = """
            LEFT JOIN LATERAL (SELECT p.due_date, p.delivery_date, p.care_mode FROM pregnancies p
                               WHERE p.user_id = rv.user_id ORDER BY p.id DESC LIMIT 1) pg ON true""";

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;
    private final ProductLikeRepository productLikeRepository;
    private final Clock clock;

    // 상품 카드마다 붙일 요약. 후기가 없는 상품도 COLLECTING 으로 맵에 넣어 준다
    public Map<Long, ProductReviewSummary> summaries(Collection<Long> productIds) {
        Map<Long, ProductReviewSummary> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        productIds.forEach(id -> out.put(id, ProductReviewSummary.COLLECTING));
        List<Long> ids = List.copyOf(new HashSet<>(productIds));
        String in = "mi.product_id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        List<Object> args = new ArrayList<>(ids);
        args.add(version());
        jdbc.query("""
                        SELECT product_id, count(*) AS people, count(*) FILTER (WHERE value < target) AS in_target
                        FROM (SELECT DISTINCT ON (product_id, user_id) product_id, user_id, value, target
                              FROM (%s) rv ORDER BY product_id, user_id, measured_at DESC, id DESC) one
                        GROUP BY product_id""".formatted(REVIEWS.formatted(in)),
                rs -> {
                    out.put(rs.getLong("product_id"),
                            ProductReviewSummary.of(rs.getInt("people"), rs.getInt("in_target")));
                },
                args.toArray());
        return out;
    }

    public ProductReviewSummary summaryOf(Long productId) {
        return summaries(List.of(productId)).get(productId);
    }

    // 상품 하나의 후기 목록. 고른 필터 안에서 5명이 안 되면 숫자도 줄도 안 준다
    public ProductReviewPageResponse page(Long productId, ProductReviewFilter filter, String cursor, int size) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new CustomException(ShopErrorCode.PRODUCT_NOT_FOUND));
        ProductReviewFilter f = filter == null ? ProductReviewFilter.ALL : filter;
        String cursorKey = "shop.reviews." + productId + "." + f;   // 다른 상품·필터에서 만든 커서는 막는다
        TimeCursor after = cursor == null || cursor.isBlank() ? null
                : TimeCursor.decode(cursor, cursorKey, ShopErrorCode.INVALID_CURSOR);
        String base = "SELECT rv.*, pg.due_date, pg.delivery_date, pg.care_mode FROM (%s) rv %s WHERE %s"
                .formatted(REVIEWS.formatted("mi.product_id = ?"), LATEST_PREGNANCY, filterSql(f));

        int[] counts = new int[2];
        jdbc.query("""
                        SELECT count(*) AS people, count(*) FILTER (WHERE value < target) AS in_target
                        FROM (SELECT DISTINCT ON (user_id) user_id, value, target FROM (%s) f
                              ORDER BY user_id, measured_at DESC, id DESC) one""".formatted(base),
                rs -> {
                    counts[0] = rs.getInt("people");
                    counts[1] = rs.getInt("in_target");
                },
                productId, version());
        ProductReviewPageResponse.Summary summary = ProductReviewPageResponse.Summary.of(counts[0], counts[1]);
        if (summary.collecting()) {
            return new ProductReviewPageResponse(summary, List.of(), null, false);
        }

        List<Object> args = new ArrayList<>(List.of(productId, version()));
        String page = "SELECT f.*,"
                // 잰 날(한국)의 임신 주수. 예정일 280일 전부터, 출산 뒤면 null (진료 리포트와 같은 계산)
                + " CASE WHEN f.due_date IS NULL THEN NULL"
                + "  WHEN (f.measured_at AT TIME ZONE 'Asia/Seoul')::date < f.due_date - 280 THEN NULL"
                + "  WHEN f.delivery_date IS NOT NULL AND (f.measured_at AT TIME ZONE 'Asia/Seoul')::date > f.delivery_date THEN NULL"
                + "  ELSE ((f.measured_at AT TIME ZONE 'Asia/Seoul')::date - (f.due_date - 280)) / 7 END AS week,"
                // 같이 먹은 것: 식약처 음식·상점 상품만 (직접 적은 이름은 누군지 드러날 수 있어서 뺀다)
                + " (SELECT array_agg(o.name ORDER BY o.id) FROM (SELECT x.id, x.name FROM meal_items x"
                + "   WHERE x.meal_id = f.meal_id AND x.deleted_at IS NULL AND x.product_id IS DISTINCT FROM f.product_id"
                + "     AND (x.food_code IS NOT NULL OR x.product_id IS NOT NULL) ORDER BY x.id LIMIT 3) o) AS with_foods"
                + " FROM (" + base + ") f";
        if (after != null) {
            page += " WHERE (f.measured_at, f.id) < (?, ?)";
            args.add(Timestamp.from(after.at()));
            args.add(after.id());
        }
        page += " ORDER BY f.measured_at DESC, f.id DESC LIMIT ?";
        args.add(size + 1);   // 하나 더 읽어서 다음 쪽이 있는지 본다

        List<Row> rows = jdbc.query(page, (rs, i) -> new Row(
                new ProductReviewPageResponse.Item(rs.getLong("id"), rs.getObject("week", Integer.class),
                        rs.getObject("minutes", Integer.class), rs.getInt("value"),
                        rs.getInt("value") < rs.getInt("target"), names(rs.getArray("with_foods"))),
                rs.getTimestamp("measured_at").toInstant()), args.toArray());
        boolean hasNext = rows.size() > size;
        List<Row> shown = hasNext ? rows.subList(0, size) : rows;
        String next = hasNext ? new TimeCursor(shown.getLast().measuredAt(), shown.getLast().item().id()).encode(cursorKey) : null;
        return new ProductReviewPageResponse(summary, shown.stream().map(Row::item).toList(), next, hasNext);
    }

    // 「산모들이 먹고 기록한 간식」: 후기가 5명 이상 모인 판매 중 상품. 사람 많은 순, 같으면 목표 안 비율 높은 순
    public ProductCardListResponse reviewed(Long userId, int size) {
        List<Product> products = productRepository.findAllByActiveTrue();
        Map<Long, ProductReviewSummary> summary = summaries(products.stream().map(Product::getId).toList());
        List<Product> picked = products.stream()
                .filter(p -> !summary.get(p.getId()).collecting())
                .sorted(Comparator.<Product>comparingInt(p -> -summary.get(p.getId()).reviewerCount())
                        .thenComparing(p -> -rate(summary.get(p.getId())))
                        .thenComparingInt(Product::getSortOrder))
                .limit(size)
                .toList();
        return new ProductCardListResponse(cards(userId, picked, summary));
    }

    // 「OO님을 위한 저당 추천」. 최근 14일 내 간식 식후 혈당을 보고 고른다
    // 1) 목표보다 높았던 날 먹은 상품은 뺀다 2) 후기 5명 이상에 목표 안 70% 이상인 상품 먼저
    // 3) 그다음 1개에 당이 적은 순, 탄수화물이 적은 순. 간식·음료만, 이유 코드에 맞는 상품만
    public ProductRecommendationResponse recommended(Long userId, int size) {
        Instant since = clock.instant().minus(Duration.ofDays(RECOMMEND_DAYS));
        // 간식 = 간식 끼니이거나 상점 상품을 고른 식사
        String snackReadings = """
                SELECT r.id, r.value, c.post_meal_target AS target, r.meal_id FROM readings r
                JOIN meals m ON m.id = r.meal_id AND m.deleted_at IS NULL
                JOIN criteria_versions c ON c.id = r.criteria_version_id AND c.post_meal_target IS NOT NULL
                WHERE r.user_id = ? AND r.reading_type = 'POST_MEAL' AND r.deleted_at IS NULL AND r.measured_at >= ?
                  AND (m.meal_slot = 'SNACK' OR EXISTS (SELECT 1 FROM meal_items x
                       WHERE x.meal_id = m.id AND x.deleted_at IS NULL AND x.product_id IS NOT NULL))""";
        int[] basis = new int[2];
        jdbc.query("SELECT count(*) AS n, count(*) FILTER (WHERE value >= target) AS above FROM (" + snackReadings + ") s",
                rs -> {
                    basis[0] = rs.getInt("n");
                    basis[1] = rs.getInt("above");
                },
                userId, Timestamp.from(since));
        Set<Long> ateHigh = new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT x.product_id FROM (%s) s JOIN meal_items x ON x.meal_id = s.meal_id
                WHERE s.value >= s.target AND x.deleted_at IS NULL AND x.product_id IS NOT NULL""".formatted(snackReadings),
                Long.class, userId, Timestamp.from(since)));

        List<Product> candidates = productRepository.findAllByActiveTrue().stream()
                .filter(p -> SNACKS.contains(p.getCategory()) && !ateHigh.contains(p.getId()))
                .toList();
        Map<Long, ProductReviewSummary> summary = summaries(candidates.stream().map(Product::getId).toList());
        Map<Long, Reason> reasons = new HashMap<>();
        candidates.forEach(p -> {
            Reason reason = reasonOf(p, summary.get(p.getId()));
            if (reason != null) {
                reasons.put(p.getId(), reason);
            }
        });
        List<Product> picked = candidates.stream()
                .filter(p -> reasons.containsKey(p.getId()))
                .sorted(Comparator.<Product>comparingInt(p -> reasons.get(p.getId()) == Reason.REVIEWED_IN_TARGET ? 0 : 1)
                        .thenComparing(p -> -rate(summary.get(p.getId())))
                        .thenComparing(Product::getSugarG, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Product::getCarbG, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(Product::getSortOrder))
                .limit(size)
                .toList();
        List<ProductSummaryResponse> cards = cards(userId, picked, summary);
        List<ProductRecommendationResponse.Item> items = cards.stream()
                .map(c -> new ProductRecommendationResponse.Item(c, reasons.get(c.id())))
                .toList();
        return new ProductRecommendationResponse(
                new ProductRecommendationResponse.Basis(RECOMMEND_DAYS, basis[0], basis[1]), items);
    }

    private static Reason reasonOf(Product p, ProductReviewSummary review) {
        if (!review.collecting() && rate(review) >= IN_TARGET_RATE) return Reason.REVIEWED_IN_TARGET;
        if (p.getSugarG() != null && p.getSugarG().compareTo(LOW_SUGAR_G) <= 0) return Reason.LOW_SUGAR;
        if (p.getCarbG() != null && p.getCarbG().compareTo(LOW_CARB_G) <= 0) return Reason.LOW_CARB;
        return null;
    }

    private List<ProductSummaryResponse> cards(Long userId, List<Product> products, Map<Long, ProductReviewSummary> summary) {
        if (products.isEmpty()) {
            return List.of();
        }
        Set<Long> liked = productLikeRepository.findLikedProductIds(userId, products.stream().map(Product::getId).toList());
        return products.stream()
                .map(p -> ProductSummaryResponse.of(p, liked.contains(p.getId()), summary.get(p.getId())))
                .toList();
    }

    private static double rate(ProductReviewSummary s) {
        return s.collecting() ? 0 : (double) s.inTargetCount() / s.reviewerCount();
    }

    private static String filterSql(ProductReviewFilter filter) {
        return switch (filter) {
            case ALL -> "true";
            case ONE_HOUR -> "rv.minutes = 60";
            case TWO_HOUR -> "rv.minutes = 120";
            // 임신 정보가 없으면 인슐린을 쓴다고 적은 적이 없는 것으로 본다
            case NO_INSULIN -> "(pg.care_mode IS NULL OR pg.care_mode <> 'INSULIN')";
        };
    }

    private static List<String> names(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static String version() {
        return ConsentType.PRODUCT_REVIEW.getVersion();
    }

    private record Row(ProductReviewPageResponse.Item item, Instant measuredAt) {
    }
}
