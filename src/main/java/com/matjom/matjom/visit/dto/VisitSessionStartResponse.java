package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

public record VisitSessionStartResponse(Long sessionId,
                                        VisitState state,
                                        OffsetDateTime startedAt,
                                        OffsetDateTime expiresAt,
                                        boolean replayed) {
}
