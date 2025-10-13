package com.matjom.matjom.feed.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LikeCreateRequestDTO {

    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;

    private Long visitId;
}
