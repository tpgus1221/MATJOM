package com.matjom.matjom.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.VisitState;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * <p>
 * 사용자의 점심 추천 여정을 끝까지 따라가 보는 통합 테스트입니다.
 * <ol>
 *     <li>회원 가입 → AccessToken/RefreshToken 확보</li>
 *     <li>추천 후보 목록(장소 검색) 조회</li>
 *     <li>선택한 장소로 방문 세션 생성</li>
 *     <li>위치 이벤트 전송(도착 직전 상태까지 서버와 동기화)</li>
 *     <li>수동 도착 확정 → 세션 상태가 ARRIVED로 전환되는지 확인</li>
 * </ol>
 * Postgres / Redis 를 Testcontainers 로 구동해 실제 운영 환경과 동일한 인프라를 재현합니다.
 * </p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LunchJourneyIntegrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2-alpine");

    private static final double BASE_LAT = 37.5665;   // 서울 시청 인근 위도 – 테스트 데이터 기준점
    private static final double BASE_LNG = 126.9780;  // 서울 시청 인근 경도

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("matjom")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("tc-init.sql");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("jwt.secret-base64", () -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("회원가입부터 장소 추천·방문 도착까지 전체 시나리오가 정상 동작한다")
    void completeLunchJourney() {
        // 0. 테스트에 사용할 장소 데이터를 직접 삽입한다. (PostGIS geography 필드 포함)
        long placeId = insertSamplePlace();
        System.out.printf("[데이터 준비] placeId=%d 샘플 장소가 삽입되었습니다.%n", placeId);

        // 1. 회원 가입과 동시에 Access / Refresh 토큰을 발급받는다.
        SignUpRequest signUpRequest = new SignUpRequest();
        String testEmail = "integration+" + UUID.randomUUID() + "@matjom.dev";
        signUpRequest.setEmail(testEmail);
        signUpRequest.setPassword("P@ssw0rd!");
        signUpRequest.setName("통합테스터");

        HttpHeaders signUpHeaders = new HttpHeaders();
        signUpHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApiResponse<LoginResponse>> signUpResponse = restTemplate.exchange(
                "/api/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest, signUpHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(signUpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<LoginResponse> signUpBody = Objects.requireNonNull(signUpResponse.getBody(), "회원가입 응답 본문이 없습니다.");
        String accessToken = Objects.requireNonNull(signUpResponse.getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                "AccessToken 헤더가 존재해야 합니다.");
        String refreshToken = signUpBody.data().getRefreshToken();
        System.out.printf("[STEP 1] 회원가입 완료 - accessToken=%s..., refreshToken=%s...%n",
                accessToken.substring(0, Math.min(accessToken.length(), 15)),
                refreshToken.substring(0, Math.min(refreshToken.length(), 15)));

        // 2. 방금 삽입한 장소가 검색 API에서 조회되는지 검증한다.
        HttpHeaders searchHeaders = new HttpHeaders();
        searchHeaders.set(HttpHeaders.AUTHORIZATION, accessToken);
        String searchUrl = String.format(
                "/api/v1/places?lat=%s&lng=%s&radius=%s&size=%s",
                BASE_LAT, BASE_LNG, 500, 10
        );
        ResponseEntity<ApiResponse<PlaceSearchResponse>> searchResponse = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                new HttpEntity<>(searchHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceSearchResponse searchData = Objects.requireNonNull(searchResponse.getBody()).data();
        assertThat(searchData.places()).isNotEmpty();
        PlaceSearchResponse.PlaceSummary firstPlace = searchData.places().get(0);
        assertThat(firstPlace.placeId()).isEqualTo(placeId);
        System.out.printf("[STEP 2] 장소 검색 성공 - '%s' (%.1fm)%n", firstPlace.name(), firstPlace.distanceMeters());

        // 3. 검색 결과를 선택해 방문 세션을 생성한다. (멱등 키 필수)
        VisitSessionStartRequest startRequest = new VisitSessionStartRequest();
        startRequest.setPlaceId(placeId);
        startRequest.setClientMode(ClientMode.NAVIGATION);

        HttpHeaders startHeaders = new HttpHeaders();
        startHeaders.set(HttpHeaders.AUTHORIZATION, accessToken);
        startHeaders.setContentType(MediaType.APPLICATION_JSON);
        startHeaders.set("Idempotency-Key", idempotencyKey());

        ResponseEntity<ApiResponse<VisitSessionStartResponse>> startResponse = restTemplate.exchange(
                "/api/v1/sessions",
                HttpMethod.POST,
                new HttpEntity<>(startRequest, startHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        VisitSessionStartResponse startData = Objects.requireNonNull(startResponse.getBody()).data();
        assertThat(startData.state()).isEqualTo(VisitState.ACTIVE);
        long sessionId = startData.sessionId();
        System.out.printf("[STEP 3] 방문 세션 생성 - sessionId=%d, expiresAt=%s%n", sessionId, startData.expiresAt());

        // 3-1. 수동 도착이 가능하도록 서버 시각을 15분 앞당긴다. (10분 이상 경과 조건을 충족시키기 위함)
        jdbcTemplate.update("UPDATE visits SET started_at = started_at - interval '15 minutes', "
                + "expired_at = expired_at - interval '15 minutes' WHERE visit_id = ?", sessionId);

        // 4. 사용자의 현재 위치를 서버에 전달한다.
        VisitPositionRequest positionRequest = new VisitPositionRequest();
        positionRequest.setLatitude(BigDecimal.valueOf(BASE_LAT));
        positionRequest.setLongitude(BigDecimal.valueOf(BASE_LNG));
        positionRequest.setAccuracyMeters(BigDecimal.valueOf(5.0));
        positionRequest.setMode(ClientMode.NAVIGATION);
        positionRequest.setRecordedAt(OffsetDateTime.now(ZoneId.of("Asia/Seoul")));

        HttpHeaders positionHeaders = new HttpHeaders();
        positionHeaders.set(HttpHeaders.AUTHORIZATION, accessToken);
        positionHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ApiResponse<VisitPositionResponse>> positionResponse = restTemplate.exchange(
                "/api/v1/sessions/" + sessionId + "/positions",
                HttpMethod.POST,
                new HttpEntity<>(positionRequest, positionHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(positionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        VisitPositionResponse positionData = Objects.requireNonNull(positionResponse.getBody()).data();
        assertThat(positionData.sessionId()).isEqualTo(sessionId);
        System.out.printf("[STEP 4] 위치 전송 - dwellSeconds=%d, accuracyPaused=%s%n",
                positionData.dwellSeconds(), positionData.accuracyPaused());

        // 5. 버튼을 눌러 수동 도착을 확정한다. (Idempotency-Key 필수)
        VisitManualArrivalRequest arrivalRequest = new VisitManualArrivalRequest();
        arrivalRequest.setLatitude(BigDecimal.valueOf(BASE_LAT));
        arrivalRequest.setLongitude(BigDecimal.valueOf(BASE_LNG));
        arrivalRequest.setAccuracyMeters(BigDecimal.valueOf(3.0));
        arrivalRequest.setRequestedBy("user");

        HttpHeaders arrivalHeaders = new HttpHeaders();
        arrivalHeaders.set(HttpHeaders.AUTHORIZATION, accessToken);
        arrivalHeaders.setContentType(MediaType.APPLICATION_JSON);
        arrivalHeaders.set("Idempotency-Key", idempotencyKey());

        ResponseEntity<ApiResponse<VisitManualArrivalResponse>> arrivalResponse = restTemplate.exchange(
                "/api/v1/sessions/" + sessionId + "/arrivals",
                HttpMethod.POST,
                new HttpEntity<>(arrivalRequest, arrivalHeaders),
                new ParameterizedTypeReference<>() {}
        );

        if (arrivalResponse.getStatusCode().is4xxClientError()) {
            ApiResponse<VisitManualArrivalResponse> errorBody = arrivalResponse.getBody();
            ErrorCode error = errorBody != null && errorBody.error() != null
                    ? ErrorCode.valueOf(errorBody.error().code())
                    : null;
            throw new IllegalStateException("수동 도착이 실패했습니다. errorCode=" + error);
        }

        assertThat(arrivalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        VisitManualArrivalResponse arrivalData = Objects.requireNonNull(arrivalResponse.getBody()).data();
        assertThat(arrivalData.state()).isEqualTo(VisitState.ARRIVED);
        assertThat(arrivalData.replayed()).isFalse();
        System.out.printf("[STEP 5] 수동 도착 확정 - arrivedAt=%s, replayed=%s%n",
                arrivalData.arrivedAt(), arrivalData.replayed());
    }

    /**
     * 테스트용 장소를 INSERT하고 생성된 PK를 반환한다.
     */
    private long insertSamplePlace() {
        final String sql = """
                INSERT INTO places (name, lat, lng, category, provider_id, phone_number,
                                    working_hours, break_time, opened_at, opened_at_source,
                                    biz_status, addr_sido, addr_sigungu, addr_eupmyeondong,
                                    addr_street, addr_detail, location)
                VALUES (?, ?, ?, ?::text[], ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                RETURNING place_id
                """;

        return jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, "통합테스트 맛집");
                ps.setBigDecimal(2, BigDecimal.valueOf(BASE_LAT));
                ps.setBigDecimal(3, BigDecimal.valueOf(BASE_LNG));
                ps.setArray(4, connection.createArrayOf("text", new String[]{"korean", "lunch"}));
                ps.setString(5, "provider-" + UUID.randomUUID());
                ps.setString(6, "02-0000-0000");
                ps.setObject(7, jsonb("{\"mon\":\"09:00-18:00\"}"));
                ps.setObject(8, jsonb("{\"mon\":\"12:00-13:00\"}"));
                ps.setDate(9, Date.valueOf("2020-01-01"));
                ps.setObject(10, jsonb("{\"source\":\"integration\"}"));
                ps.setString(11, "OPEN");
                ps.setString(12, "서울특별시");
                ps.setString(13, "중구");
                ps.setString(14, "태평로1가");
                ps.setString(15, "세종대로 110");
                ps.setString(16, "1층");
                ps.setDouble(17, BASE_LNG);
                ps.setDouble(18, BASE_LAT);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("장소 샘플 데이터 삽입에 실패했습니다.", ex);
            }
        });
    }

    private String idempotencyKey() {
        return "it-" + UUID.randomUUID();
    }

    private PGobject jsonb(String value) throws Exception {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(value);
        return json;
    }
}
