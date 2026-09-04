package com.swyp.ploutos.disclosure.entity;

import java.time.LocalDateTime;

import com.swyp.ploutos.stock.entity.Stocks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "announcement")
public class Announcement{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id", nullable = false)
    private Integer announcementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stocks stockId;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "type", length = 30)
    private String type;

    @Column(name = "date")
    private LocalDateTime announcedAt;

    protected Announcement() {
    }
}