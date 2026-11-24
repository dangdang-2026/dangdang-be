package com.dangdang.domain.chat.service;

import com.dangdang.domain.chat.dto.response.ChatAskResponse;
import com.dangdang.domain.chat.dto.response.ChatMessageResponse;
import com.dangdang.domain.chat.dto.response.ChatPageResponse;
import com.dangdang.domain.chat.entity.ChatMessage;
import com.dangdang.domain.chat.entity.ChatPayload;
import com.dangdang.domain.chat.enums.ChatKind;
import com.dangdang.domain.chat.enums.ChatRole;
import com.dangdang.domain.chat.exception.ChatErrorCode;
import com.dangdang.domain.chat.infra.ChatClient;
import com.dangdang.domain.chat.repository.ChatMessageRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.global.ai.AiFeature;
import com.dangdang.global.ai.AiGuard;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 당당이에게 물어보기 (ASK). 순서: 1) 위험 신호 규칙(AI 안 감) 2) 문지기(동의·하루 상한) 3) AI 4) 기록 숫자는 Spring
// AI 서버를 기다리는 동안 DB 연결을 잡지 않도록 이 메서드에는 @Transactional을 붙이지 않는다 (저장은 save가 각각)
@Service
@RequiredArgsConstructor
public class ChatService {

    static final String FAILED_TEXT = "지금은 답을 만들지 못했어요. 잠시 뒤 다시 물어봐 주세요.";
    static final String UNKNOWN_TEXT = "그 내용은 당당이 가진 자료에 없어요. 다니는 병원에 물어봐 주세요.";
    static final String CONSENT_TEXT = "내 기록을 보려면 「기록 사용 동의」가 필요해요. 마이 › AI 설정에서 켤 수 있어요.";
    static final String RECORDS_TEXT = "요청하신 기록이에요.";
    // AI에 안 보내는 답 — 다음 질문의 history에도 넣지 않는다 (리뷰 #79)
    private static final Set<ChatKind> NOT_FOR_AI = Set.of(ChatKind.RISK, ChatKind.DOSE);

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ConsentService consentService;
    private final AiGuard aiGuard;
    private final ChatClient chatClient;
    private final RecordsCardService recordsCardService;
    private final Clock clock;

    public ChatAskResponse ask(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        // 1) 위험 신호·약 용량: 동의·상한과 상관없이 바로 정해진 안내 (AI로 안 보낸다)
        Optional<RiskSignalRules.Hit> hit = RiskSignalRules.match(content);
        if (hit.isPresent()) {
            ChatMessage question = chatMessageRepository.save(ChatMessage.question(user, content, clock.instant()));
            ChatKind kind = hit.get().type() == RiskSignalRules.Type.DOSE ? ChatKind.DOSE : ChatKind.RISK;
            ChatMessage answer = chatMessageRepository.save(ChatMessage.answer(user, kind, hit.get().text(),
                    ChatPayload.ofActions(hit.get().actions()), null, null, clock.instant()));
            return pair(question, answer);
        }

        // 2) 문지기 — 오늘 상한이면 여기서 429 (아무것도 저장 안 함). 일반 질문은 국외 이전 동의 없이도 답한다 (11/23)
        AiGuard.AiCall call = aiGuard.begin(userId, AiFeature.CHAT);
        List<ChatClient.Turn> history = history(userId);          // 이번 질문을 저장하기 전에 읽는다 (위험 신호면 안 읽음)
        // 내 기록(건강정보)을 OpenAI(국외)로 보내려면 기록 사용 동의와 국외 이전 동의가 둘 다 있어야 한다 (PR #120 리뷰)
        boolean recordsAllowed = consentService.isGranted(userId, ConsentType.AI_CHAT_RECORDS)
                && consentService.isGranted(userId, ConsentType.OVERSEAS_TRANSFER);
        ChatMessage question = chatMessageRepository.save(ChatMessage.question(user, content, clock.instant()));

        // 3) AI 서버 → 4) 답 만들기. 어디서 어떤 예외가 나도 오늘 횟수를 돌려주고 FAILED로 끝낸다 (리뷰 #79)
        try {
            ChatClient.Reply reply = chatClient.ask(call.requestId(), content, recordsAllowed, history);
            ChatMessage answer = toAnswer(user, userId, reply, recordsAllowed, call.requestId());
            if (answer == null) {
                aiGuard.fail(call, reply == null || reply.kind() == null ? "EMPTY_REPLY" : "UNKNOWN_KIND");
                return pair(question, failed(user, call));
            }
            aiGuard.finish(call, reply.promptVersion(), answer.getKind().name());
            return pair(question, answer);
        } catch (RuntimeException e) {
            aiGuard.fail(call, e.getClass().getSimpleName());
            return pair(question, failed(user, call));
        }
    }

    // 4) 종류별로 화면에 그릴 것 만들기. 모르는 종류·빈 답이면 null
    private ChatMessage toAnswer(User user, Long userId, ChatClient.Reply reply, boolean recordsAllowed,
                                 String requestId) {
        if (reply == null || reply.kind() == null) {
            return null;
        }
        String pv = reply.promptVersion();
        return switch (reply.kind()) {
            case "RECORDS" -> recordsAllowed && reply.records() != null
                    ? saveAnswer(user, ChatKind.RECORDS, textOr(reply.text(), RECORDS_TEXT),
                    ChatPayload.ofRecords(recordsCardService.build(userId, reply.records())), requestId, pv)
                    : saveAnswer(user, ChatKind.CONSENT_NEEDED, CONSENT_TEXT, null, requestId, pv);
            case "QUESTION_DRAFT" -> saveAnswer(user, ChatKind.QUESTION_DRAFT,
                    textOr(reply.text(), "진료 때 이렇게 물어보면 어떨까요?"),
                    ChatPayload.ofDraft(reply.questionDraft()), requestId, pv);
            // 출처가 없거나 글이 비었으면 보여 주지 않는다 (ASK-05, 리뷰 #79: 글 없이 출처만 오면 500이 났음)
            case "INFO" -> reply.sources() == null || reply.sources().isEmpty() || isBlank(reply.text())
                    ? saveAnswer(user, ChatKind.UNKNOWN, UNKNOWN_TEXT, null, requestId, pv)
                    : saveAnswer(user, ChatKind.INFO, reply.text(), ChatPayload.ofSources(reply.sources().stream()
                    .map(s -> new ChatPayload.Source(s.title(), s.url())).toList()), requestId, pv);
            case "UNKNOWN" -> saveAnswer(user, ChatKind.UNKNOWN, textOr(reply.text(), UNKNOWN_TEXT), null,
                    requestId, pv);
            default -> null;
        };
    }

    private ChatMessage failed(User user, AiGuard.AiCall call) {
        return saveAnswer(user, ChatKind.FAILED, FAILED_TEXT, null, call.requestId(), null);
    }

    // 대화 목록 — cursorId보다 오래된 것을 최신 순으로 (위로 스크롤하며 불러와도 겹치지 않게, 리뷰 #79)
    @Transactional(readOnly = true)
    public ChatPageResponse list(Long userId, Long cursorId, int size) {
        long before = cursorId == null ? Long.MAX_VALUE : cursorId;
        List<ChatMessage> rows = chatMessageRepository.findByUserIdAndIdLessThanOrderByIdDesc(
                userId, before, Limit.of(size + 1));                    // 하나 더 읽어서 다음 장이 있는지 본다
        boolean hasNext = rows.size() > size;
        List<ChatMessage> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursorId = hasNext ? page.get(page.size() - 1).getId() : null;
        return new ChatPageResponse(page.stream().map(ChatMessageResponse::from).toList(), size, hasNext, nextCursorId);
    }

    // 대화 지우기 — 바로 삭제 (보관 기간 결정 10/22)
    @Transactional
    public int deleteAll(Long userId) {
        return chatMessageRepository.deleteAllByUserId(userId);
    }

    // 도움이 됐나요 (ASK-08) — 당당이 답에만
    @Transactional
    public ChatMessageResponse rate(Long userId, Long messageId, boolean helpful) {
        ChatMessage message = chatMessageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (message.getRole() != ChatRole.ASSISTANT) {
            throw new CustomException(ChatErrorCode.CANNOT_RATE);
        }
        message.rate(helpful);
        return ChatMessageResponse.from(message);
    }

    // 바로 앞 대화 6개를 오래된 순으로. 실패한 답, 위험 신호·약 용량 답과 그 질문은 뺀다 (AI로 안 보내는 말, 리뷰 #79)
    private List<ChatClient.Turn> history(Long userId) {
        List<ChatClient.Turn> turns = new ArrayList<>();
        boolean skipQuestion = false;                           // 최신 순이라 답 다음에 그 질문이 온다
        for (ChatMessage m : chatMessageRepository.findTop6ByUserIdOrderByIdDesc(userId)) {
            if (m.getRole() == ChatRole.ASSISTANT) {
                skipQuestion = NOT_FOR_AI.contains(m.getKind());
                if (!skipQuestion && m.getKind() != ChatKind.FAILED) {
                    turns.add(new ChatClient.Turn("assistant", m.getContent()));
                }
            } else {
                if (!skipQuestion) {
                    turns.add(new ChatClient.Turn("user", m.getContent()));
                }
                skipQuestion = false;
            }
        }
        Collections.reverse(turns);
        return turns;
    }

    private ChatMessage saveAnswer(User user, ChatKind kind, String text, ChatPayload payload,
                                   String requestId, String promptVersion) {
        return chatMessageRepository.save(
                ChatMessage.answer(user, kind, text, payload, requestId, promptVersion, clock.instant()));
    }

    private static String textOr(String text, String fallback) {
        return isBlank(text) ? fallback : text;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static ChatAskResponse pair(ChatMessage question, ChatMessage answer) {
        return new ChatAskResponse(ChatMessageResponse.from(question), ChatMessageResponse.from(answer));
    }
}
