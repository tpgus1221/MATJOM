package com.matjom.matjom.visit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitCardResponseDTO {
    private Long visitId;
    private Long placeId;
    private String placeName;
    private OffsetDateTime arrivedAt;
    private boolean reviewed;
    private UUID reviewId;
    private UUID likeId;
    private boolean liked;
    private boolean reviewAllowed;
    private boolean likeAllowed;
    private boolean reviewEditable;
    private boolean reviewDeletable;
}
