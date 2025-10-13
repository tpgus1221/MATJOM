package com.matjom.matjom.feed.repository;

import com.matjom.matjom.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReadRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u.name FROM User u WHERE u.id = :userId")
    // 목적: 사용자 UUID로 이름만 가져온다
    // 필요 이유: 응답 DTO에서 userId 대신 이름을 보여주기 위해서다
    // 로직: 전체 엔티티 로딩 없이 name 필드만 선택해 Optional로 반환한다
    Optional<String> findNameById(@Param("userId") UUID userId); // 9월 26일 최종: 리뷰 응답용 이름 조회
}
