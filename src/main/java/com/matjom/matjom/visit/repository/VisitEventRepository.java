package com.matjom.matjom.visit.repository;

import com.matjom.matjom.visit.entity.VisitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {
}
