package com.swyp.ploutos.calendar;

import java.time.LocalDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "calendar_events")
public class CalendarEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long calendarId;

    @Column(nullable = false)
    private Long stockId;	// FK : Stocks.stockId

    @Column(length = 200, nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime eventAt;
    
    @Column(length = 500)
    private String content;

    @Column(length = 50)
    private String actualValue;

    protected CalendarEvents() {
    	
    }
    
}
