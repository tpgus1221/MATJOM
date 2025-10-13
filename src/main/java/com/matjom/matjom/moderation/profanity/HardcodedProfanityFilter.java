package com.matjom.matjom.moderation.profanity;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class HardcodedProfanityFilter implements ProfanityFilter {

    private final Set<String> blacklist = Set.of("욕설1", "욕설2", "비속어3");

    @Override
    public void validate(String text) {
        if (hasBlacklistedWord(text)) {
            throw new FeedException(ErrorCode.REVIEW_BAD_LANGUAGE, "금칙어가 포함되어 있습니다.");
        }
    }

    private boolean hasBlacklistedWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
        return blacklist.stream().anyMatch(normalized::contains);
    }
}
