package com.dangdang.domain.post.service;

import com.dangdang.domain.post.dto.request.PostPrecheckRequest;
import com.dangdang.domain.post.dto.response.PostPrecheckResponse;
import com.dangdang.domain.post.infra.PostCheckClient;
import com.dangdang.domain.post.infra.PrecheckKindStore;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.service.ConsentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostPrecheckService {

    private final PostCheckClient postCheckClient;
    private final ConsentService consentService;
    private final PrecheckKindStore kindStore;

    // DB를 안 건드려서 @Transactional 이 필요 없다 (동의 읽기는 ConsentService 안에서 readOnly). 글 내용은 로그에 남기지 않는다
    public PostPrecheckResponse precheck(Long userId, PostPrecheckRequest request) {
        // U-04 「확인했어요」에 (지금 버전으로) 동의한 사람만 약·용량 문장을 GPT(해외)로 한 번 더 본다. 안 했으면 규칙만 (국내 AI 서버)
        boolean allowLlm = consentService.isGranted(userId, ConsentType.COMMUNITY_AI_CHECK);
        try {
            PostCheckClient.Result result = postCheckClient.check(request.title(), request.content(), allowLlm);
            if (result == null) {
                return PostPrecheckResponse.notChecked();
            }
            log.info("[Precheck] 사용자: {}, 걸린 문장: {}개, GPT 확인: {}",
                    userId, result.issues() == null ? 0 : result.issues().size(), result.llmChecked());
            PostPrecheckResponse response = PostPrecheckResponse.from(result);
            // 같은 본문으로 글을 쓰면 이 종류로 운영자 처리함에 넣는다 (PostService.create). 걸린 게 없으면 예전 기록을 지운다
            kindStore.save(userId, request.content(),
                    response.issues().stream().map(PostPrecheckResponse.Issue::kind).filter(Objects::nonNull).distinct().toList());
            return response;
        } catch (RestClientException e) {
            log.warn("[Precheck] AI 서버 실패 - 검사 없이 통과: {}", e.getClass().getSimpleName());
            return PostPrecheckResponse.notChecked();
        }
    }
}
