package com.swyp.ploutos.market.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "markets")
public class Markets {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "markets_id", nullable = false)
    private Long marketsId;

    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Markets() {
    }
}