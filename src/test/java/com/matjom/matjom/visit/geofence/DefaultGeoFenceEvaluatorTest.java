package com.matjom.matjom.visit.geofence;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;
import com.matjom.matjom.visit.entity.VisitState;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultGeoFenceEvaluatorTest {

    private static final double METERS_PER_LAT_DEGREE = 111320.0D;

    private DefaultGeoFenceEvaluator evaluator;
    private Place place;

    @BeforeEach
    void setUp() {
        evaluator = new DefaultGeoFenceEvaluator();
        place = Mockito.mock(Place.class);
        Mockito.when(place.getLatitude()).thenReturn(new BigDecimal("37.5665"));
        Mockito.when(place.getLongitude()).thenReturn(new BigDecimal("126.9780"));
    }

    @Test
    void evaluateMarksArrivedAfterRequiredDwell() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 1L);

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        VisitPosition first = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("5.0"),
                ClientMode.NAVIGATION,
                start);
        GeoFenceEvaluationResult firstResult = evaluator.evaluate(visit, first);
        visit.updateLastPosition(first.getLatitude(), first.getLongitude(), first.getAccuracyMeter(), first.getReceivedAt());

        assertThat(firstResult.getResultingState()).isEqualTo(VisitState.ACTIVE);
        assertThat(visit.getDwellStartedAt()).isEqualTo(start);

        OffsetDateTime mid = start.plusSeconds(120L);
        VisitPosition second = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("4.0"),
                ClientMode.NAVIGATION,
                mid);
        GeoFenceEvaluationResult secondResult = evaluator.evaluate(visit, second);
        visit.updateLastPosition(second.getLatitude(), second.getLongitude(), second.getAccuracyMeter(), second.getReceivedAt());
        assertThat(secondResult.getDwellSeconds()).isGreaterThanOrEqualTo(120L);
        assertThat(visit.getState()).isEqualTo(VisitState.ACTIVE);

        OffsetDateTime end = start.plusSeconds(185L);
        VisitPosition third = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("3.0"),
                ClientMode.NAVIGATION,
                end);
        GeoFenceEvaluationResult thirdResult = evaluator.evaluate(visit, third);
        visit.updateLastPosition(third.getLatitude(), third.getLongitude(), third.getAccuracyMeter(), third.getReceivedAt());

        assertThat(thirdResult.getResultingState()).isEqualTo(VisitState.ARRIVED);
        assertThat(thirdResult.getDwellSeconds()).isGreaterThanOrEqualTo(180L);
        assertThat(visit.getState()).isEqualTo(VisitState.ARRIVED);
        assertThat(visit.getArrivedAt()).isEqualTo(end);
    }

    @Test
    void evaluateKeepsDwellWithinGraceAndResetsAfterGrace() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 2L);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);

        VisitPosition inside = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                null,
                ClientMode.NAVIGATION,
                start);
        evaluator.evaluate(visit, inside);
        visit.updateLastPosition(inside.getLatitude(), inside.getLongitude(), inside.getAccuracyMeter(), inside.getReceivedAt());
        OffsetDateTime dwellStart = visit.getDwellStartedAt();
        assertThat(dwellStart).isEqualTo(start);

        VisitPosition outsideWithinGrace = new VisitPosition(visit,
                new BigDecimal("37.5700"),
                new BigDecimal("126.9800"),
                null,
                ClientMode.NAVIGATION,
                start.plusSeconds(9L));
        evaluator.evaluate(visit, outsideWithinGrace);
        visit.updateLastPosition(outsideWithinGrace.getLatitude(), outsideWithinGrace.getLongitude(), outsideWithinGrace.getAccuracyMeter(), outsideWithinGrace.getReceivedAt());
        assertThat(visit.getDwellStartedAt()).isEqualTo(dwellStart);

        VisitPosition outsideAfterGrace = new VisitPosition(visit,
                new BigDecimal("37.5700"),
                new BigDecimal("126.9800"),
                null,
                ClientMode.NAVIGATION,
                start.plusSeconds(11L));
        evaluator.evaluate(visit, outsideAfterGrace);
        visit.updateLastPosition(outsideAfterGrace.getLatitude(), outsideAfterGrace.getLongitude(), outsideAfterGrace.getAccuracyMeter(), outsideAfterGrace.getReceivedAt());
        assertThat(visit.getDwellStartedAt()).isNull();
    }

    @Test
    void evaluatePausesWhenAccuracyTooLow() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 3L);

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        VisitPosition first = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("5.0"),
                ClientMode.NAVIGATION,
                start);
        GeoFenceEvaluationResult firstResult = evaluator.evaluate(visit, first);
        visit.updateLastPosition(first.getLatitude(), first.getLongitude(), first.getAccuracyMeter(), first.getReceivedAt());
        assertThat(firstResult.isAccuracyPaused()).isFalse();

        VisitPosition second = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("3.0"),
                ClientMode.NAVIGATION,
                start.plusSeconds(120L));
        GeoFenceEvaluationResult secondResult = evaluator.evaluate(visit, second);
        visit.updateLastPosition(second.getLatitude(), second.getLongitude(), second.getAccuracyMeter(), second.getReceivedAt());
        assertThat(secondResult.isAccuracyPaused()).isFalse();
        long dwellBeforePause = secondResult.getDwellSeconds();
        assertThat(dwellBeforePause).isGreaterThanOrEqualTo(120L);

        VisitPosition inaccurate = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("50.0"),
                ClientMode.NAVIGATION,
                start.plusSeconds(150L));
        GeoFenceEvaluationResult pausedResult = evaluator.evaluate(visit, inaccurate);
        visit.updateLastPosition(inaccurate.getLatitude(), inaccurate.getLongitude(), inaccurate.getAccuracyMeter(), inaccurate.getReceivedAt());
        assertThat(pausedResult.isAccuracyPaused()).isTrue();
        assertThat(pausedResult.getDwellSeconds()).isEqualTo(dwellBeforePause);

        VisitPosition resume = new VisitPosition(visit,
                new BigDecimal("37.5666"),
                new BigDecimal("126.9781"),
                new BigDecimal("5.0"),
                ClientMode.NAVIGATION,
                start.plusSeconds(210L));
        GeoFenceEvaluationResult resumeResult = evaluator.evaluate(visit, resume);
        visit.updateLastPosition(resume.getLatitude(), resume.getLongitude(), resume.getAccuracyMeter(), resume.getReceivedAt());
        assertThat(resumeResult.isAccuracyPaused()).isFalse();
        assertThat(resumeResult.getDwellSeconds()).isGreaterThan(dwellBeforePause);
    }

    @Test
    void evaluateDoesNotArriveWhenDistanceOrDwellBelowThreshold() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 4L);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);

        BigDecimal insideLat = offsetLatitude(place.getLatitude(), 29.9D);
        BigDecimal insideLng = place.getLongitude();
        VisitPosition first = new VisitPosition(visit,
                insideLat,
                insideLng,
                new BigDecimal("5.0"),
                ClientMode.NAVIGATION,
                start);
        evaluator.evaluate(visit, first);
        visit.updateLastPosition(first.getLatitude(), first.getLongitude(), first.getAccuracyMeter(), first.getReceivedAt());

        OffsetDateTime almostEnough = start.plusSeconds(174L);
        VisitPosition second = new VisitPosition(visit,
                insideLat,
                insideLng,
                new BigDecimal("5.0"),
                ClientMode.NAVIGATION,
                almostEnough);
        GeoFenceEvaluationResult secondResult = evaluator.evaluate(visit, second);
        visit.updateLastPosition(second.getLatitude(), second.getLongitude(), second.getAccuracyMeter(), second.getReceivedAt());

        assertThat(secondResult.getResultingState()).isEqualTo(VisitState.ACTIVE);
        assertThat(secondResult.getDwellSeconds()).isLessThan(180L);
        assertThat(visit.getState()).isEqualTo(VisitState.ACTIVE);
    }

    @Test
    void evaluateArrivesExactlyAtThreshold() throws Exception {
        Visit visit = createVisit();
        setVisitId(visit, 5L);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);

        BigDecimal boundaryLat = offsetLatitude(place.getLatitude(), 30.0D);
        BigDecimal boundaryLng = place.getLongitude();

        VisitPosition first = new VisitPosition(visit,
                boundaryLat,
                boundaryLng,
                new BigDecimal("4.0"),
                ClientMode.NAVIGATION,
                start);
        evaluator.evaluate(visit, first);
        visit.updateLastPosition(first.getLatitude(), first.getLongitude(), first.getAccuracyMeter(), first.getReceivedAt());

        OffsetDateTime thresholdMet = start.plusSeconds(180L);
        VisitPosition second = new VisitPosition(visit,
                boundaryLat,
                boundaryLng,
                new BigDecimal("3.5"),
                ClientMode.NAVIGATION,
                thresholdMet);
        GeoFenceEvaluationResult result = evaluator.evaluate(visit, second);
        visit.updateLastPosition(second.getLatitude(), second.getLongitude(), second.getAccuracyMeter(), second.getReceivedAt());

        assertThat(result.getResultingState()).isEqualTo(VisitState.ARRIVED);
        assertThat(result.getDwellSeconds()).isGreaterThanOrEqualTo(180L);
        assertThat(visit.getState()).isEqualTo(VisitState.ARRIVED);
        assertThat(visit.getArrivedAt()).isEqualTo(thresholdMet);
    }

    private void setVisitId(Visit visit, Long id) throws Exception {
        Field field = Visit.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(visit, id);
    }

    private Visit createVisit() {
        return new Visit(new User("user@test.com", "tester", "pw", AuthProvider.LOCAL), place, ClientMode.NAVIGATION, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private BigDecimal offsetLatitude(BigDecimal base, double meters) {
        double deltaDegrees = meters / METERS_PER_LAT_DEGREE;
        BigDecimal delta = BigDecimal.valueOf(deltaDegrees);
        return base.add(delta).setScale(7, RoundingMode.HALF_UP);
    }
}
