package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.dto.PlaceSearchCursor;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceRepository {

    // Native PostGIS query: 검색 반경 내 장소를 거리 ASC, 동일 거리 시 ID ASC로 정렬해 커서 페이징한다.
    // - user_point: 요청 위경도를 geography 포인트로 변환(미터 단위 거리 계산).
    // - ranked: ST_DWithin으로 반경 내 장소를 필터링하고 ST_Distance로 거리(m)를 산출.
    // - 최종 SELECT: 커서(distance,lastId) 조건과 정렬을 적용해 pageSize(+1)만큼 가져온다.

	// [핵심 설계 포인트]
	// 1) ST_DWithin: 반경 내 후보만 추출(인덱스 사용 전제) → 후보 범위를 줄여 비용 절감.
	// 2) ST_Distance: meter 단위 거리 산출(타원체 계산 옵션). 목록 정렬키이자 커서 기준키.
	// 3) 커서 WHERE: (distance > D) OR (distance = D AND id > I) → 중복/누락 방지(안정 정렬).
	// 4) ORDER BY distance ASC, id ASC → 커서와 동일 기준으로 "끊김 없는" 페이징.
	// 5) LIMIT :limit → +1 조회로 다음 페이지/상한 초과 감지.
    private static final String SEARCH_SQL = """
            WITH user_point AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
            ), ranked AS (
                SELECT p.place_id,
                       p.name,
                       ST_Distance(p.location, up.point, true) AS distance_m,
                       p.lat,
                       p.lng
                FROM places p
                CROSS JOIN user_point up
                WHERE ST_DWithin(p.location, up.point, :radius, true)
            )
            SELECT place_id,
                   name,
                   distance_m,
                   lat,
                   lng
            FROM ranked
            WHERE (:cursorDistance IS NULL
                OR distance_m > :cursorDistance
                OR (distance_m = :cursorDistance AND place_id > :cursorLastId))
            ORDER BY distance_m ASC, place_id ASC
            LIMIT :limit
            """;

    private static final String ROULETTE_SQL = """
            WITH user_point AS (
                SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
            )
            SELECT p.place_id,
                   p.name,
                   p.category,
                   ST_Distance(p.location, up.point, true) AS distance_m,
                   p.lat,
                   p.lng
            FROM places p
            CROSS JOIN user_point up
            WHERE ST_DWithin(p.location, up.point, :radius, true)
              AND (:categories IS NULL OR p.category && :categories)
            ORDER BY p.place_id
            LIMIT :limit
            """;


    private static final RowMapper<PlaceSummary> PLACE_SUMMARY_ROW_MAPPER = new RowMapper<PlaceSummary>() {
        @Override
        public PlaceSummary mapRow(java.sql.ResultSet rs, int rowNum) throws SQLException {
            return new PlaceSummary(
                    rs.getLong("place_id"),
                    rs.getString("name"),
                    rs.getDouble("distance_m"),
                    rs.getDouble("lat"),
                    rs.getDouble("lng"));
        }
    };

    private static final RowMapper<RouletteCandidate> ROULETTE_CANDIDATE_ROW_MAPPER = new RowMapper<RouletteCandidate>() {
        @Override
        public RouletteCandidate mapRow(java.sql.ResultSet rs, int rowNum) throws SQLException {
            Array categoryArray = rs.getArray("category");
            List<String> categoryList = categoryArray == null
                    ? List.of()
                    : Arrays.asList((String[]) categoryArray.getArray());
            return new RouletteCandidate(
                    rs.getLong("place_id"),
                    rs.getString("name"),
                    rs.getDouble("distance_m"),
                    List.copyOf(categoryList),
                    rs.getDouble("lat"),
                    rs.getDouble("lng")
            );
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


	/**
	 * [비즈니스 목적]
	 * - "내 위치(lat,lng) 기준"으로 반경 radius(m) 안의 장소를 "거리 ASC, (동거리) ID ASC"로 안정 정렬하여 페이지네이션 반환.
	 * - 커서("distance:lastId")를 이용해 무상태 연속 페이징을 보장(중복/누락 없이 다음 페이지 이어보기).
	 *
	 * [제품 정책 연결]
	 * - 반경: 검색 UX 범위(목록/룰렛용). 도착판정(30m/3분)과 별개.
	 * - 페이지 상한: 서비스 절대 상한 500. +1 조회로 nextCursor/상한 초과 감지.
	 * - 거리 단위: meter. 서버에서 PostGIS(geography)로 계산하여 일관성 유지(클라 재계산 금지 권장).
	 */
    public List<PlaceSummary> search(double lat,
                                     double lng,
                                     double radiusMeters,
                                     int limit,
                                     PlaceSearchCursor cursor,
                                     String unusedFilters) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit)
                // 명시적으로 타입을 지정하지 않으면 PostgreSQL이 NULL 파라미터 타입을 추론하지 못해 에러가 발생한다.
                .addValue("cursorDistance",
                        cursor == null ? null : cursor.distanceMeters(),
                        java.sql.Types.DOUBLE)
                .addValue("cursorLastId",
                        cursor == null ? null : cursor.lastPlaceId(),
                        java.sql.Types.BIGINT);

        // TODO: filters 적용 로직은 후속 태스크에서 구현

        return jdbcTemplate.query(SEARCH_SQL, params, PLACE_SUMMARY_ROW_MAPPER);
    }

	/**
	 * [비즈니스 목적]
	 * - 룰렛 "후보풀" 생성: 반경(radius, m) 안에서 카테고리/영업상태 등 필터로 **후보 상한(limit)** 만큼만 수집.
	 * - 이 메서드는 "선택"을 하지 않는다. 오직 "풀 구성"까지 담당한다.
	 *
	 * [정책 연결]
	 * - limit은 비용/지연을 제어하기 위한 "풀 상한"이다. 너무 작으면 트렁케이션 바이어스가 생긴다(가까운/인기도 높은 구역만 반영될 가능성).
	 * - 균등추출은 Service에서 수행한다(거리/정렬 무관). 여기서 ORDER BY는 의미 없다(고정 시 오히려 바이어스↑).
	 */
    public List<RouletteCandidate> findRouletteCandidates(double lat,
                                                          double lng,
                                                          double radiusMeters,
                                                          List<String> categories,
                                                          int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lng", lng)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit)
                .addValue("categories", categories == null || categories.isEmpty() ? null : categories.toArray(new String[0]));

        return jdbcTemplate.query(ROULETTE_SQL, params, ROULETTE_CANDIDATE_ROW_MAPPER);
    }
}
