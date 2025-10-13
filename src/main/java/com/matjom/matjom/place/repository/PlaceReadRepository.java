package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.entity.Place;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReadRepository extends JpaRepository<Place, Long> {

    @Query("SELECT p.name FROM Place p WHERE p.id = :placeId")
    // 목적: 장소 식별자로 이름만 조회한다
    // 필요 이유: 리뷰/좋아요 응답에 장소명을 포함해 가독성을 높인다
    // 로직: Place 엔티티 전체 대신 name 컬럼만 선택해 Optional로 반환한다
    Optional<String> findNameById(@Param("placeId") Long placeId); // 9월 26일 최종: 리뷰 응답용 장소 이름
}
