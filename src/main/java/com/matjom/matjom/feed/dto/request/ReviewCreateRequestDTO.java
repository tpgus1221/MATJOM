package com.matjom.matjom.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDTO {
    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;

    private Long visitId;

    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 1, max = 140, message = "리뷰는 1-140자 사이로 작성해주세요")
    private String text;
}
