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
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.ai.AiFeature;
import com.dangdang.global.ai.AiGuard;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 당당이에게 물어보기 (ASK). 순서: 1) 위험 신호 규칙(AI 안 감) 2) 건강정보 동의 3) 문지기(동의·하루 상한) 4) AI 5) 기록 숫자는 Spring
// AI 서버를 기다리는 동안 DB 연결을 잡지 않도록 이 메서드에는 @Transactional을 붙이지 않는다
// 저장은 짧은 트랜잭션으로 각각 — 그때마다 건강정보 동의를 잠그고 다시 본다 (SensitiveConsentGuard, 철회와 겹치지 않게)
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
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ChatAskResponse ask(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        // 1) 위험 신호·약 용량: 국외 이전 동의·상한과 상관없이 바로 정해진 안내 (AI로 안 보낸다)
        //    건강정보(SENSITIVE) 동의가 없어도 안내는 돌려준다 — 문서 버전이 올라 재동의가 필요한 사람이 챗봇 화면을 열어 둔 채
        //    「피가 비쳐요」를 보내도 응급 안내를 받아야 해서. 이때는 대화 기록에 남기지 않는다 (id 없이 화면에만)
        Optional<RiskSignalRules.Hit> hit = RiskSignalRules.match(content);
        if (hit.isPresent()) {
            return ruleAnswer(user, userId, content, hit.get());
        }
        // 2) 질문·답이 건강 이야기라 건강정보(SENSITIVE) 동의가 있어야 남긴다 — 없으면 403 USER_40302
        //    AI 횟수를 쓰기 전에 먼저 보고, 저장할 때 잠그고 한 번 더 본다 (그사이 철회했을 수 있어서)
        if (!consentService.isGranted(userId, ConsentType.SENSITIVE)) {
            throw new CustomException(UserErrorCode.SENSITIVE_CONSENT_REQUIRED);
        }

        // 3) 문지기 — 국외 이전 동의가 없으면 403 AI_40301, 오늘 상한이면 429 (아무것도 저장 안 함) (Q-01 「필수」, 11/24)
        AiGuard.AiCall call = aiGuard.begin(userId, AiFeature.CHAT);
        List<ChatClient.Turn> history = history(userId);          // 이번 질문을 저장하기 전에 읽는다 (위험 신호면 안 읽음)
        // 국외 이전 동의는 문지기가, 건강정보 동의는 2)에서 봤다 → 내 기록을 쓰려면 기록 사용 동의만 더 본다
        // (철회 뒤 기록 사용 동의만 다시 켜도 2)에서 막혀서, 건강정보 동의 없이 기록을 보내는 일은 없다)
        boolean recordsAllowed = consentService.isGranted(userId, ConsentType.AI_CHAT_RECORDS);
        ChatMessage question;
        try {
            question = transactionTemplate.execute(status -> {
                sensitiveConsentGuard.require(userId);
                return chatMessageRepository.save(ChatMessage.question(user, content, clock.instant()));
            });
        } catch (RuntimeException e) {
            aiGuard.fail(call, "NOT_SAVED");   // 2) 뒤에 철회함 → AI를 안 불렀으니 횟수를 돌려준다
            throw e;
        }

        // 4) AI 서버 → 5) 답 만들기. 어디서 어떤 예외가 나도 FAILED로 끝내고 오늘 횟수를 돌려준다 (리뷰 #79)
        ChatMessage answer;
        String failReason = null;
        String promptVersion = null;
        try {
            ChatClient.Reply reply = chatClient.ask(call.requestId(), content, recordsAllowed, history);
            answer = toAnswer(user, userId, reply, recordsAllowed, call.requestId());
            if (answer == null) {
                failReason = reply == null || reply.kind() == null ? "EMPTY_REPLY" : "UNKNOWN_KIND";
                answer = failed(user, call);
            } else {
                promptVersion = reply.promptVersion();
            }
        } catch (RuntimeException e) {
            failReason = e.getClass().getSimpleName();
            answer = failed(user, call);
        }
        // 횟수 기록(finish/fail)은 답을 저장한 뒤에 — AI를 기다리는 사이 철회해서 저장이 403이면 답을 못 받았으니 돌려준다
        ChatMessage saved;
        try {
            saved = saveAnswer(userId, question, answer);
        } catch (RuntimeException e) {
            aiGuard.fail(call, "NOT_SAVED");
            throw e;
        }
        if (failReason != null) {
            aiGuard.fail(call, failReason);
        } else {
            aiGuard.finish(call, promptVersion, saved.getKind().name());
        }
        return pair(question, saved);
    }

    // 위험 신호·약 용량 안내. 건강정보 동의가 있으면 질문·답을 남기고, 없으면(또는 저장 직전 철회됐으면) 남기지 않고 화면에만
    private ChatAskResponse ruleAnswer(User user, Long userId, String content, RiskSignalRules.Hit hit) {
        ChatKind kind = hit.type() == RiskSignalRules.Type.DOSE ? ChatKind.DOSE : ChatKind.RISK;
        ChatMessage question = ChatMessage.question(user, content, clock.instant());
        ChatMessage answer = ChatMessage.answer(user, kind, hit.text(), ChatPayload.ofActions(hit.actions()),
                null, null, clock.instant());
        if (!consentService.isGranted(userId, ConsentType.SENSITIVE)) {
            return pair(question, answer);
        }
        try {
            return transactionTemplate.execute(status -> {
                sensitiveConsentGuard.require(userId);
                return pair(chatMessageRepository.save(question), chatMessageRepository.save(answer));
            });
        } catch (CustomException e) {
            if (e.getErrorCode() != UserErrorCode.SENSITIVE_CONSENT_REQUIRED) {
                throw e;
            }
            // 확인과 저장 사이에 철회됨 → 저장은 롤백됐고 안내만 돌려준다. 롤백으로 붙은 id는 화면에 안 내보낸다
            return pair(ChatMessage.question(user, content, clock.instant()), ChatMessage.answer(user, kind,
                    hit.text(), ChatPayload.ofActions(hit.actions()), null, null, clock.instant()));
        }
    }

    // 답 저장 — 건강정보 동의를 잠그고 다시 본다 (AI를 기다리는 사이 철회했으면 403, 답을 남기지 않음)
    // 그사이 질문이 지워졌으면(대화 지우기·철회 뒤 다시 동의) 답만 남지 않게 저장하지 않고 화면에만 돌려준다
    private ChatMessage saveAnswer(Long userId, ChatMessage question, ChatMessage answer) {
        return transactionTemplate.execute(status -> {
            sensitiveConsentGuard.require(userId);
            return chatMessageRepository.existsById(question.getId()) ? chatMessageRepository.save(answer) : answer;
        });
    }

    // 5) 종류별로 화면에 그릴 답 만들기 (저장은 saveAnswer). 모르는 종류·빈 답이면 null
    private ChatMessage toAnswer(User user, Long userId, ChatClient.Reply reply, boolean recordsAllowed,
                                 String requestId) {
        if (reply == null || reply.kind() == null) {
            return null;
        }
        String pv = reply.promptVersion();
        return switch (reply.kind()) {
            case "RECORDS" -> recordsAllowed && reply.records() != null
                    ? answerOf(user, ChatKind.RECORDS, textOr(reply.text(), RECORDS_TEXT),
                    ChatPayload.ofRecords(recordsCardService.build(userId, reply.records())), requestId, pv)
                    : answerOf(user, ChatKind.CONSENT_NEEDED, CONSENT_TEXT, null, requestId, pv);
            case "QUESTION_DRAFT" -> answerOf(user, ChatKind.QUESTION_DRAFT,
                    textOr(reply.text(), "진료 때 이렇게 물어보면 어떨까요?"),
                    ChatPayload.ofDraft(reply.questionDraft()), requestId, pv);
            // 출처가 없거나 글이 비었으면 보여 주지 않는다 (ASK-05, 리뷰 #79: 글 없이 출처만 오면 500이 났음)
            case "INFO" -> reply.sources() == null || reply.sources().isEmpty() || isBlank(reply.text())
                    ? answerOf(user, ChatKind.UNKNOWN, UNKNOWN_TEXT, null, requestId, pv)
                    : answerOf(user, ChatKind.INFO, reply.text(), ChatPayload.ofSources(reply.sources().stream()
                    .map(s -> new ChatPayload.Source(s.title(), s.url())).toList()), requestId, pv);
            case "UNKNOWN" -> answerOf(user, ChatKind.UNKNOWN, textOr(reply.text(), UNKNOWN_TEXT), null,
                    requestId, pv);
            default -> null;
        };
    }

    private ChatMessage failed(User user, AiGuard.AiCall call) {
        return answerOf(user, ChatKind.FAILED, FAILED_TEXT, null, call.requestId(), null);
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
    // 회원 줄을 FOR UPDATE로 잠그고 지운다 → 답 저장(saveAnswer, FOR SHARE)이 질문이 있는지 보고 저장하는 사이에 끼어들지 못하고
    // 차례로 실행된다 (저장 중이면 끝날 때까지 기다렸다가 같이 지움, 지운 뒤면 저장 쪽이 질문 없음을 보고 답을 안 남김)
    @Transactional
    public int deleteAll(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
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

    private ChatMessage answerOf(User user, ChatKind kind, String text, ChatPayload payload,
                                 String requestId, String promptVersion) {
        return ChatMessage.answer(user, kind, text, payload, requestId, promptVersion, clock.instant());
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
