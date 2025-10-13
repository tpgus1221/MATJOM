package com.matjom.matjom.user.repository;

import com.matjom.matjom.user.entity.DeletedUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedUserRepository extends JpaRepository<DeletedUser, UUID> {
}
