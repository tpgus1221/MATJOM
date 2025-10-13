package com.matjom.matjom.user.entity;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.UUID)
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

    protected User() {
        // JPA
    }

    public User(String email, String name, String password, AuthProvider provider) {
        this.email = email;
        this.name = name;
        this.password = password;
        this.provider = provider;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public boolean isLocalAccount() {
        return provider == AuthProvider.LOCAL;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePassword(String password) {
        this.password = password;
    }

    public static User createLocalUser(String email, String name, String password) {
        return new User(email, name, password, AuthProvider.LOCAL);
    }

    public static User createOAuthUser(String email, String name, String password) {
        return new User(email, name, password, AuthProvider.GOOGLE);
    }
}
