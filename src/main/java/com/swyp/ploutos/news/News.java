package com.swyp.ploutos.news;

import java.time.LocalDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String url;
    
    @Column(nullable = false, length = 100)
    private String publisher;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(length = 40)
    private String category;

    @Column(nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    protected News() {
    	
    }
    
}
