package com.matjom.matjom.visit.geofence;

import com.matjom.matjom.visit.entity.VisitState;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class GeoFenceEvaluationResult {

    private final VisitState resultingState;
    private final long dwellSeconds;
    private final boolean accuracyPaused;

}
