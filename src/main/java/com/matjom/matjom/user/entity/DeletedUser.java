package com.matjom.matjom.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deleted_users")
public class DeletedUser {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Email
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at", nullable = false)
    private OffsetDateTime deletedAt;

    protected DeletedUser() {
        // JPA
    }

    private DeletedUser(UUID id, String email, String name, String password, AuthProvider provider,
                        OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime deletedAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.password = password;
        this.provider = provider;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static DeletedUser from(User user, OffsetDateTime deletedAt) {
        return new DeletedUser(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPassword(),
                user.getProvider(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                deletedAt
        );
    }
}
