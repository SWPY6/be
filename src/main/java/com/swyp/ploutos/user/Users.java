package com.swyp.ploutos.user;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.swyp.ploutos.common.enums.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, unique = true)
    private String email;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private String password;

    @Column(length = 50, nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Users() {

    }

    public Users(String loginId, String email, String password, String name, UserStatus status) {
        this.loginId = loginId;
        this.email = email;
        this.password = password;
        this.name = name;
        this.status = status;
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

}
