 package com.swyp.ploutos.disclosure;

import java.time.LocalDateTime;

import com.swyp.ploutos.common.enums.AnnouncementType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "announcements")
public class Announcements{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long announcementId;

    @Column(nullable = false)
    private Long stockId;	// FK : Stocks.stockId

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private AnnouncementType type;

    @Column(nullable = false)
    private LocalDateTime announcedAt;

    protected Announcements() {
    	
    }
    
}
