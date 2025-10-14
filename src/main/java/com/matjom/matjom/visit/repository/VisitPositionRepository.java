package com.matjom.matjom.visit.repository;

import com.matjom.matjom.visit.entity.VisitPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitPositionRepository extends JpaRepository<VisitPosition, Long> {
}
