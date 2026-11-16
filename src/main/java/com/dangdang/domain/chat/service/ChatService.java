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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// 당당이에게 물어보기 (ASK). 순서: 1) 위험 신호 규칙(AI 안 감) 2) 문지기(동의·하루 상한) 3) AI 4) 기록 숫자는 Spring
// AI 서버를 기다리는 동안 DB 연결을 잡지 않도록 이 메서드에는 @Transactional을 붙이지 않는다 (저장은 save가 각각)
@Service
@RequiredArgsConstructor
public class ChatService {

    static final String FAILED_TEXT = "지금은 답을 만들지 못했어요. 잠시 뒤 다시 물어봐 주세요.";
    static final String UNKNOWN_TEXT = "그 내용은 당당이 가진 자료에 없어요. 다니는 병원에 물어봐 주세요.";
    static final String CONSENT_TEXT = "내 기록을 보려면 「기록 사용 동의」가 필요해요. 마이 › AI 설정에서 켤 수 있어요.";
    static final String RECORDS_TEXT = "요청하신 기록이에요.";

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
        List<ChatClient.Turn> history = history(userId);          // 이번 질문을 저장하기 전에 읽는다

        // 1) 위험 신호·약 용량: 동의·상한과 상관없이 바로 정해진 안내 (AI로 안 보낸다)
        Optional<RiskSignalRules.Hit> hit = RiskSignalRules.match(content);
        if (hit.isPresent()) {
            ChatMessage question = chatMessageRepository.save(ChatMessage.question(user, content, clock.instant()));
            ChatKind kind = hit.get().type() == RiskSignalRules.Type.DOSE ? ChatKind.DOSE : ChatKind.RISK;
            ChatMessage answer = chatMessageRepository.save(ChatMessage.answer(user, kind, hit.get().text(),
                    ChatPayload.ofActions(hit.get().actions()), null, null, clock.instant()));
            return pair(question, answer);
        }

        // 2) 문지기 — 동의가 없거나 오늘 상한이면 여기서 403·429 (아무것도 저장 안 함)
        AiGuard.AiCall call = aiGuard.begin(userId, AiFeature.CHAT);
        boolean recordsAllowed = consentService.isGranted(userId, ConsentType.AI_CHAT_RECORDS);
        ChatMessage question = chatMessageRepository.save(ChatMessage.question(user, content, clock.instant()));

        // 3) AI 서버
        ChatClient.Reply reply;
        try {
            reply = chatClient.ask(call.requestId(), content, recordsAllowed, history);
        } catch (RestClientException e) {
            aiGuard.fail(call, e.getClass().getSimpleName());
            return pair(question, saveAnswer(user, ChatKind.FAILED, FAILED_TEXT, null, call.requestId(), null));
        }
        if (reply == null || reply.kind() == null) {
            aiGuard.fail(call, "EMPTY_REPLY");
            return pair(question, saveAnswer(user, ChatKind.FAILED, FAILED_TEXT, null, call.requestId(), null));
        }

        // 4) 종류별로 화면에 그릴 것 만들기
        ChatMessage answer = switch (reply.kind()) {
            case "RECORDS" -> recordsAllowed && reply.records() != null
                    ? saveAnswer(user, ChatKind.RECORDS, textOr(reply.text(), RECORDS_TEXT),
                    ChatPayload.ofRecords(recordsCardService.build(userId, reply.records())),
                    call.requestId(), reply.promptVersion())
                    : saveAnswer(user, ChatKind.CONSENT_NEEDED, CONSENT_TEXT, null, call.requestId(),
                    reply.promptVersion());
            case "QUESTION_DRAFT" -> saveAnswer(user, ChatKind.QUESTION_DRAFT, textOr(reply.text(), "진료 때 이렇게 물어보면 어떨까요?"),
                    ChatPayload.ofDraft(reply.questionDraft()), call.requestId(), reply.promptVersion());
            case "INFO" -> reply.sources() == null || reply.sources().isEmpty()
                    // 출처 없는 정보는 보여 주지 않는다 (ASK-05)
                    ? saveAnswer(user, ChatKind.UNKNOWN, UNKNOWN_TEXT, null, call.requestId(), reply.promptVersion())
                    : saveAnswer(user, ChatKind.INFO, reply.text(), ChatPayload.ofSources(reply.sources().stream()
                            .map(s -> new ChatPayload.Source(s.title(), s.url())).toList()),
                    call.requestId(), reply.promptVersion());
            case "UNKNOWN" -> saveAnswer(user, ChatKind.UNKNOWN, textOr(reply.text(), UNKNOWN_TEXT), null,
                    call.requestId(), reply.promptVersion());
            default -> null;
        };
        if (answer == null) {
            aiGuard.fail(call, "UNKNOWN_KIND");
            return pair(question, saveAnswer(user, ChatKind.FAILED, FAILED_TEXT, null, call.requestId(), null));
        }
        aiGuard.finish(call, reply.promptVersion(), answer.getKind().name());
        return pair(question, answer);
    }

    @Transactional(readOnly = true)
    public ChatPageResponse list(Long userId, int page, int size) {
        Slice<ChatMessage> slice = chatMessageRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size));
        return new ChatPageResponse(slice.map(ChatMessageResponse::from).getContent(), page, size, slice.hasNext());
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

    // 바로 앞 대화 6개를 오래된 순으로 (실패한 답은 빼고)
    private List<ChatClient.Turn> history(Long userId) {
        List<ChatClient.Turn> turns = new ArrayList<>();
        for (ChatMessage m : chatMessageRepository.findTop6ByUserIdOrderByIdDesc(userId)) {
            if (m.getKind() != ChatKind.FAILED) {
                turns.add(new ChatClient.Turn(m.getRole() == ChatRole.USER ? "user" : "assistant", m.getContent()));
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
        return text == null || text.isBlank() ? fallback : text;
    }

    private static ChatAskResponse pair(ChatMessage question, ChatMessage answer) {
        return new ChatAskResponse(ChatMessageResponse.from(question), ChatMessageResponse.from(answer));
    }
}
