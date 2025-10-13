package com.matjom.matjom.feed.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeStatusResponseDTO {
    private boolean liked;
    private UUID likeId;
}
