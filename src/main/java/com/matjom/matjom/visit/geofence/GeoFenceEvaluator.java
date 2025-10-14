package com.matjom.matjom.visit.geofence;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;

public interface GeoFenceEvaluator {

    GeoFenceEvaluationResult evaluate(Visit visit, VisitPosition position);
}
