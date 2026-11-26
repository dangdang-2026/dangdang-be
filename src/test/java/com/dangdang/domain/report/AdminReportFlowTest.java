package com.dangdang.domain.report;

import com.dangdang.domain.post.entity.Post;
import com.dangdang.domain.post.repository.PostRepository;
import com.dangdang.domain.report.service.SystemReportService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 신고 처리함 (COM-09 · U-14). 지수가 쓰고 민지·하나가 신고, 운영자가 처리한다
class AdminReportFlowTest extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;
    Cookie hana;
    Cookie admin;

    @Autowired PostRepository postRepository;
    @Autowired SystemReportService systemReportService;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), clock.instant()));
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), clock.instant()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        hana = login("hana@x.com");
        admin = login("admin@x.com");                       // 역할은 로그인할 때 토큰에 들어간다
        profile(jisu, "말랑한 두부");
        profile(minji, "포근한 고구마");
        profile(hana, "새콤한 자두");
    }

    @AfterEach
    void cleanCommunity() { // reports·admin_actions·posts는 회원을 지워도 남는다 → 먼저 지운다
        jdbc.update("DELETE FROM admin_actions");
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void profile(Cookie who, String nickname) throws Exception {
        postJson(who, "/api/community/profiles", "{\"nickname\":\"" + nickname + "\"}").andExpect(status().isCreated());
    }

    private String write(Cookie who, String title) throws Exception {
        String res = postJson(who, "/api/posts", "{\"category\":\"MIND\",\"title\":\"" + title + "\",\"content\":\"본문\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private String comment(Cookie who, String postId, String content) throws Exception {
        String res = postJson(who, "/api/posts/" + postId + "/comments", "{\"content\":\"" + content + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private void report(Cookie who, String type, String id, String reason) throws Exception {
        postJson(who, "/api/reports", "{\"targetType\":\"" + type + "\",\"targetId\":\"" + id + "\",\"reason\":\"" + reason + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 처리함은_대상별로_묶고_신고_많은_순() throws Exception {
        String a = write(jisu, "이 약 두 알 먹으면 괜찮아요");
        String b = write(jisu, "오늘 수치 공유해요");
        report(minji, "POST", a, "MEDICAL_ADVICE");
        report(hana, "POST", a, "ABUSE");
        clock.set(NOW.plusSeconds(60));
        report(minji, "POST", b, "NUMBER_COMPARE");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[*].targetId", contains(a, b)))       // 2건 > 1건(더 최신)
                .andExpect(jsonPath("$.data.items[0].reportCount").value(2))
                .andExpect(jsonPath("$.data.items[0].reasons", contains("ABUSE", "MEDICAL_ADVICE")))
                .andExpect(jsonPath("$.data.items[0].preview").value("이 약 두 알 먹으면 괜찮아요"))
                .andExpect(jsonPath("$.data.items[0].state").value("VISIBLE"));
    }

    @Test
    void 위급_신고는_신고_수가_적고_오래돼도_처리함_맨_위() throws Exception {
        String crisis = write(jisu, "그냥 사라지고 싶어요");
        report(minji, "POST", crisis, "CRISIS");
        clock.set(NOW.plusSeconds(60));
        String many = write(jisu, "광고 글");
        report(minji, "POST", many, "AD_SALES");
        report(hana, "POST", many, "MISINFO");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].targetId", contains(crisis, many)))   // 1건·더 오래됨이어도 먼저
                .andExpect(jsonPath("$.data.items[0].reasons", contains("CRISIS")))
                .andExpect(jsonPath("$.data.items[1].reasons", contains("AD_SALES", "MISINFO")));
    }

    @Test
    void 위급_신고끼리는_신고_많은_순_같으면_최근_순() throws Exception {
        String one = write(jisu, "위급 하나");
        String two = write(jisu, "위급 둘");
        report(minji, "POST", two, "CRISIS");
        report(hana, "POST", two, "ABUSE");                              // 사유가 섞여도 CRISIS가 하나 있으면 위급
        clock.set(NOW.plusSeconds(60));
        report(minji, "POST", one, "CRISIS");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.items[*].targetId", contains(two, one)))
                .andExpect(jsonPath("$.data.items[0].reasons", contains("ABUSE", "CRISIS")));
    }

    @Test
    void 자동_신고는_사람_신고_수에_안_섞이고_사람_위급_신고_다음_사람_신고_많은_글보다_위() throws Exception {
        String human = write(jisu, "사람이 걱정해서 신고한 글");
        String auto = write(jisu, "안내를 보고도 올린 글");
        String many = write(jisu, "광고 글");
        clock.set(NOW.plusSeconds(60));
        report(minji, "POST", human, "CRISIS");
        systemReportService.queuePostForReview(Long.valueOf(auto), List.of("MIND", "URGENT_MEDICAL", "DOSE", "WHAT"));
        report(minji, "POST", auto, "ABUSE");
        clock.set(NOW.plusSeconds(120));
        report(minji, "POST", many, "AD_SALES");
        report(hana, "POST", many, "AD_SALES");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.items[*].targetId", contains(human, auto, many)))
                .andExpect(jsonPath("$.data.items[0].systemReportCount").value(0))
                .andExpect(jsonPath("$.data.items[1].reportCount").value(1))           // 사람 신고만
                .andExpect(jsonPath("$.data.items[1].systemReportCount").value(2))     // 모르는 종류(WHAT)는 버림
                .andExpect(jsonPath("$.data.items[1].reasons", contains("ABUSE", "CRISIS", "MEDICAL_ADVICE")))
                .andExpect(jsonPath("$.data.items[2].reportCount").value(2));

        mvc.perform(get("/api/admin/reports/POST/" + auto).cookie(admin))
                .andExpect(jsonPath("$.data.reports[*].source", containsInAnyOrder("USER", "SYSTEM", "SYSTEM")))
                .andExpect(jsonPath("$.data.reports[?(@.source == 'SYSTEM')].detail", containsInAnyOrder(
                        "올리기 전 안내를 보고도 올림 (자동: MIND, URGENT_MEDICAL)", "올리기 전 안내를 보고도 올림 (자동: DOSE)")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE source = 'SYSTEM' AND reporter_id IS NULL",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void 위급은_가리기_사유로_못_고르고_잘못된_건강_정보는_고를_수_있다() throws Exception {
        String a = write(jisu, "탄수화물 끊으세요");
        report(minji, "POST", a, "MISINFO");
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"CRISIS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"MISINFO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("HIDDEN"));
    }

    @Test
    void 가리면_사용자에게_HIDDEN으로_보이고_처리함에서_빠진다() throws Exception {
        String a = write(jisu, "이 약 두 알 먹으면 괜찮아요");
        report(minji, "POST", a, "MEDICAL_ADVICE");
        report(hana, "POST", a, "MEDICAL_ADVICE");

        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"MEDICAL_ADVICE\",\"memo\":\"용량 권유\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("HIDDEN"))
                .andExpect(jsonPath("$.data.handledReports").value(2));

        mvc.perform(get("/api/posts/" + a).cookie(minji))                                 // U-14
                .andExpect(jsonPath("$.data.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.data.hiddenReason").value("MEDICAL_ADVICE"))
                .andExpect(jsonPath("$.data.title").doesNotExist());
        mvc.perform(get("/api/posts").cookie(minji))                                      // 목록에서도 빠진다
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.totalCount").value(0));
        mvc.perform(get("/api/admin/reports").param("status", "HIDDEN").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].targetId").value(a))
                .andExpect(jsonPath("$.data.items[0].state").value("HIDDEN"));
    }

    @Test
    void 원문에는_가린_글_본문과_글쓴이_회원번호_처리기록이_나온다() throws Exception {
        String a = write(jisu, "이 약 두 알 먹으면 괜찮아요");
        report(minji, "POST", a, "MEDICAL_ADVICE");
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"MEDICAL_ADVICE\",\"memo\":\"용량 권유\"}");

        mvc.perform(get("/api/admin/reports/POST/" + a).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("이 약 두 알 먹으면 괜찮아요"))
                .andExpect(jsonPath("$.data.content").value("본문"))
                .andExpect(jsonPath("$.data.authorUserId").value(jisuId))
                .andExpect(jsonPath("$.data.authorNickname").value("말랑한 두부"))
                .andExpect(jsonPath("$.data.state").value("HIDDEN"))
                .andExpect(jsonPath("$.data.reports[0].reason").value("MEDICAL_ADVICE"))
                .andExpect(jsonPath("$.data.reports[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.reports[0].reporterId").doesNotExist())      // 신고한 사람은 안 나온다
                .andExpect(jsonPath("$.data.actions[0].action").value("HIDE"))
                .andExpect(jsonPath("$.data.actions[0].memo").value("용량 권유"));
    }

    @Test
    void 문제없음은_글을_그대로_두고_신고만_정리() throws Exception {
        String a = write(jisu, "오늘 수치 공유해요");
        report(minji, "POST", a, "NUMBER_COMPARE");

        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin))     // 메모 없이도 된다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("VISIBLE"))
                .andExpect(jsonPath("$.data.handledReports").value(1));

        mvc.perform(get("/api/posts/" + a).cookie(minji))
                .andExpect(jsonPath("$.data.title").value("오늘 수치 공유해요"));
        mvc.perform(get("/api/admin/reports").cookie(admin)).andExpect(jsonPath("$.data.totalCount").value(0));
        mvc.perform(get("/api/admin/reports").param("status", "DISMISSED").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].targetId").value(a));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin))     // 두 번째는 정리할 신고가 없다
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40903"));
    }

    @Test
    void 댓글을_가리면_댓글_목록에서_빠진다() throws Exception {
        String a = write(jisu, "글");
        String c = comment(minji, a, "이 약 반 알로 줄여 보세요");
        report(jisu, "COMMENT", c, "MEDICAL_ADVICE");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].targetType").value("COMMENT"))
                .andExpect(jsonPath("$.data.items[0].postId").value(a))
                .andExpect(jsonPath("$.data.items[0].preview").value("이 약 반 알로 줄여 보세요"));
        postJson(admin, "/api/admin/reports/COMMENT/" + c + "/hide", "{\"reason\":\"MEDICAL_ADVICE\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/posts/" + a + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void 되돌리면_다시_보이고_두번_가리기나_안_가린_걸_되돌리기는_409() throws Exception {
        String a = write(jisu, "글");
        report(minji, "POST", a, "ABUSE");
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}").andExpect(status().isOk());
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40901"));

        postJson(admin, "/api/admin/reports/POST/" + a + "/unhide", "{\"memo\":\"이의 신청 받아들임\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("VISIBLE"));
        mvc.perform(get("/api/posts/" + a).cookie(minji)).andExpect(jsonPath("$.data.title").value("글"));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/unhide").cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40902"));
        mvc.perform(get("/api/admin/reports/POST/" + a).cookie(admin))
                .andExpect(jsonPath("$.data.actions[*].action", contains("UNHIDE", "HIDE")));
    }

    @Test
    void 운영자가_아니면_403_없는_대상은_404_사유가_없으면_400() throws Exception {
        mvc.perform(get("/api/admin/reports").cookie(minji)).andExpect(status().isForbidden());
        postJson(minji, "/api/admin/reports/POST/1/hide", "{\"reason\":\"ABUSE\"}").andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/reports/POST/999999").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40401"));
        String a = write(jisu, "글");
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{}").andExpect(status().isBadRequest());
        postJson(minji, "/api/reports", "{\"targetType\":\"POST\",\"targetId\":\"" + a + "\",\"reason\":\"WHAT\"}")
                .andExpect(status().isBadRequest());                          // 모르는 신고 사유
        mvc.perform(get("/api/admin/reports/PHOTO/1").cookie(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void 탈퇴로_글이_통째로_지워져도_남은_신고를_문제없음으로_정리한다() throws Exception {
        String a = write(jisu, "지워질 글");
        report(minji, "POST", a, "ABUSE");
        jdbc.update("DELETE FROM posts WHERE id = ?", Long.valueOf(a));                // 탈퇴하면 글 행이 실제로 지워진다

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].state").value("DELETED"));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("DELETED"))
                .andExpect(jsonPath("$.data.handledReports").value(1));
        mvc.perform(get("/api/admin/reports").cookie(admin)).andExpect(jsonPath("$.data.totalCount").value(0));
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40401"));
    }

    @Test
    void 지운_글은_가리거나_되돌릴_수_없고_문제없음으로만_정리한다() throws Exception {
        String a = write(jisu, "지운 글");
        report(minji, "POST", a, "ABUSE");
        jdbc.update("UPDATE posts SET deleted_at = now() WHERE id = ?", Long.valueOf(a));  // 글쓴이가 지움

        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40904"));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/unhide").cookie(admin))
                .andExpect(jsonPath("$.errorCode").value("REPORT_40904"));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("DELETED"));
    }

    @Test
    void 가린_대상은_문제없음이_안_되고_되돌리면_신고가_문제없음_탭으로_간다() throws Exception {
        String a = write(jisu, "글");
        report(minji, "POST", a, "ABUSE");
        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}").andExpect(status().isOk());

        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40901"));
        mvc.perform(post("/api/admin/reports/POST/" + a + "/unhide").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handledReports").value(1));
        mvc.perform(get("/api/admin/reports").param("status", "HIDDEN").cookie(admin))
                .andExpect(jsonPath("$.data.totalCount").value(0));
        mvc.perform(get("/api/admin/reports").param("status", "DISMISSED").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].targetId").value(a))
                .andExpect(jsonPath("$.data.items[0].state").value("VISIBLE"));
    }

    @Test
    void 문제없음_뒤에_가리면_그_신고도_가림_탭으로_옮긴다() throws Exception {
        String a = write(jisu, "글");
        report(minji, "POST", a, "ABUSE");
        mvc.perform(post("/api/admin/reports/POST/" + a + "/dismiss").cookie(admin)).andExpect(status().isOk());

        postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handledReports").value(1));
        mvc.perform(get("/api/admin/reports").param("status", "DISMISSED").cookie(admin))
                .andExpect(jsonPath("$.data.totalCount").value(0));
        mvc.perform(get("/api/admin/reports").param("status", "HIDDEN").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].state").value("HIDDEN"));
    }

    @Test
    void 미리보기는_이모지를_반으로_자르지_않고_원문에_고친_시각이_나온다() throws Exception {
        String title = "가".repeat(39) + "😀😀";                                        // 글자 41개 (UTF-16으로는 43)
        String a = write(jisu, title);
        report(minji, "POST", a, "ABUSE");

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].preview").value("가".repeat(39) + "😀…"));
        mvc.perform(get("/api/admin/reports/POST/" + a).cookie(admin))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void 글쓴이가_읽어_둔_뒤_가려져도_글쓴이_저장이_가림을_풀지_않는다() throws Exception {
        String a = write(jisu, "원래 제목");
        report(minji, "POST", a, "MEDICAL_ADVICE");

        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            Post stale = postRepository.findById(Long.valueOf(a)).orElseThrow();   // 글쓴이가 먼저 읽음 (아직 안 가림)
            inOtherThread(() -> postJson(admin, "/api/admin/reports/POST/" + a + "/hide",
                    "{\"reason\":\"MEDICAL_ADVICE\"}").andExpect(status().isOk()));  // 그 사이 운영자가 가리고 커밋
            stale.update(null, "고친 제목", null, clock.instant());                  // 글쓴이 저장은 그 뒤에 커밋
        });

        mvc.perform(get("/api/posts/" + a).cookie(minji))
                .andExpect(jsonPath("$.data.visibility").value("HIDDEN"));         // 가림이 그대로
        mvc.perform(get("/api/admin/reports/POST/" + a).cookie(admin))
                .andExpect(jsonPath("$.data.title").value("고친 제목"))               // 글쓴이 수정도 그대로
                .andExpect(jsonPath("$.data.state").value("HIDDEN"));
    }

    @Test
    void 운영자_둘이_동시에_가리면_한_명만_성공하고_기록은_한_줄() throws Exception {
        String a = write(jisu, "글");
        report(minji, "POST", a, "ABUSE");

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> hide = () -> {
            start.await();
            return postJson(admin, "/api/admin/reports/POST/" + a + "/hide", "{\"reason\":\"ABUSE\"}")
                    .andReturn().getResponse().getStatus();
        };
        List<Integer> codes;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(hide);
            Future<Integer> second = pool.submit(hide);
            start.countDown();                                                       // 둘을 한꺼번에 출발
            codes = List.of(first.get(), second.get());
        }

        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_actions WHERE action = 'HIDE'", Integer.class))
                .isEqualTo(1);
    }

    // 다른 스레드 = 다른 트랜잭션. 끝날 때까지 기다리고, 안에서 난 실패는 그대로 던진다
    private void inOtherThread(Callable<?> work) {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(work).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // admin #5 신고 처리 화면: 탭 숫자, 글쓴이 닉네임, 처리한 운영자 (be #167)
    @Test
    void 처리함에_탭_숫자와_글쓴이와_처리한_운영자가_온다() throws Exception {
        String a = write(jisu, "이 약 두 알 먹으면 괜찮아요");
        String b = write(minji, "혈당 패치 공동구매 하실 분");
        String c = write(jisu, "오늘 수치 공유해요");
        report(minji, "POST", a, "MEDICAL_ADVICE");
        report(hana, "POST", a, "MEDICAL_ADVICE");
        report(jisu, "POST", b, "AD_SALES");
        report(hana, "POST", c, "NUMBER_COMPARE");
        clock.set(NOW.plusSeconds(600));
        postJson(admin, "/api/admin/reports/POST/" + b + "/hide", "{\"reason\":\"AD_SALES\",\"memo\":\"광고\"}")
                .andExpect(status().isOk());
        postJson(admin, "/api/admin/reports/POST/" + c + "/dismiss", "{}").andExpect(status().isOk());

        mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.data.counts.received").value(1))
                .andExpect(jsonPath("$.data.counts.hidden").value(1))
                .andExpect(jsonPath("$.data.counts.dismissed").value(1))
                .andExpect(jsonPath("$.data.items[0].authorNickname").value("말랑한 두부"))
                .andExpect(jsonPath("$.data.items[0].handledAt").doesNotExist());
        mvc.perform(get("/api/admin/reports?status=HIDDEN").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].authorNickname").value("포근한 고구마"))
                .andExpect(jsonPath("$.data.items[0].handledAt").value(NOW.plusSeconds(600).toString()))
                .andExpect(jsonPath("$.data.items[0].handledByName").value("운영자"));
        mvc.perform(get("/api/admin/reports/POST/" + b).cookie(admin))
                .andExpect(jsonPath("$.data.actions[0].adminName").value("운영자"));
    }
}
