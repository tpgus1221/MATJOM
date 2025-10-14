package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * [역할 정의 — 중요]
 * - 이 JPA 레포지토리는 CRUD/관리성 조회 전용입니다.
 * - 제품의 핵심 기능인 **근처 탐색(반경/거리 정렬/커서 페이징)** 은
 *   PostGIS 연산이 필요한 관계로 `PlaceRepository`(JdbcTemplate + Native SQL)에서 처리합니다.
 *
 * [왜 분리했는가]
 * - Spring Data JPA의 파생쿼리는 `ST_DWithin/Distance` 같은 지오 연산을 표현하기 어렵습니다.
 * - "거리 ASC + (동거리) ID ASC" 안정 정렬 및 "+1 조회" 기반 커서 설계는
 *   네이티브 SQL이 명확하고 성능·가시성 측면에서 유리합니다.
 *
 * [여기서 하는 일 — 예시]
 * - 단건 조회/저장: `findById`, `save`
 * - 운영/관리 콘솔: 이름 부분검색, 다건 ID 조회, 중복 검사 등
 * - **하지 않는 일**: 반경 내 목록, 거리순 정렬, 커서 페이지네이션 (→ PlaceRepository)
 */
@Repository
public interface PlaceJpaRepository extends JpaRepository<Place, Long> {
}
