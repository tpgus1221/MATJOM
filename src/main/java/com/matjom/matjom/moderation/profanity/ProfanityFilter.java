package com.matjom.matjom.moderation.profanity;

public interface ProfanityFilter {
    void validate(String text); // 9월 30일 최종: 외부 노출 메서드는 검증만 수행
}
