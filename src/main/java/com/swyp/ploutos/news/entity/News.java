package com.swyp.ploutos.news.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "title", length = 100)
    private String title;

    @Lob
    @Column(name = "url", columnDefinition = "LONGTEXT")
    private String url;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected News() {
    }
}