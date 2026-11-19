package com.dangdang.domain.visit.enums;

// 진료 질문을 누가 만들었는지. 클라이언트가 정하지 않고 서버가 정함 (AI 출처를 사용자가 꾸미지 못하게)
public enum QuestionSource {
    USER,      // 사용자가 직접 적음
    CHATBOT    // 챗봇 대화에서 가져옴 (챗봇 쪽 기능에서 씀)
}
