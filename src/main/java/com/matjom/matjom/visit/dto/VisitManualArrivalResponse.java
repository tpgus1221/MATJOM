package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

public record VisitManualArrivalResponse(Long sessionId,
                                          VisitState state,
                                          OffsetDateTime arrivedAt,
                                          String requestedBy,
                                          boolean replayed) {
}
