package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

public record VisitPositionResponse(Long positionId,
                                    Long sessionId,
                                    VisitState state,
                                    OffsetDateTime recordedAt,
                                    long dwellSeconds,
                                    boolean accuracyPaused) {
}
