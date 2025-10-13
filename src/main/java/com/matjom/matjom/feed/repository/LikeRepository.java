package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.likes.Like;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, UUID> {

    boolean existsByVisitId(Long visitId);

    Optional<Like> findByVisitId(Long visitId);

    Optional<Like> findByIdAndUserId(UUID likeId, UUID userId);

    List<Like> findByVisitIdInAndDeletedAtIsNull(Collection<Long> visitIds);
}
