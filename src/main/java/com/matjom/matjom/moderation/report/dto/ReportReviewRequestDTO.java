package com.matjom.matjom.moderation.report.dto;

import com.matjom.matjom.moderation.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReportReviewRequestDTO {

    // 수정제안 2024-09-26: 신고 사유는 필수이며 enum 값으로 제한합니다.
    @NotNull(message = "신고 사유(reason)는 필수입니다.")
    private ReportReason reason;

    // 수정제안 2024-09-26: 세부 설명은 선택 사항이지만 최대 길이를 제한합니다.
    @Size(max = 500, message = "세부 설명(description)은 500자를 넘을 수 없습니다.")
    private String description;
}