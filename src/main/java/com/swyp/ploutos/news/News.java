package com.swyp.ploutos.news;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.swyp.ploutos.common.enums.NewsCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long newsId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2100)
    private String url;
    
    @Column(nullable = false, length = 100)
    private String publisher;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsCategory category;

    @Column(nullable = false, updatable = false)
    private LocalDateTime publishedAt;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    protected News() {
    	
    }
    
}
