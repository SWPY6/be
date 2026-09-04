package com.swyp.ploutos.calendar.entity;

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
@Table(name = "calendar_event")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stocks stockId;

    @Column(name = "event_name", length = 200, nullable = false)
    private String eventName;

    @Column(name = "event_datetime", nullable = false)
    private LocalDateTime eventDatetime;

    @Column(name = "actual_value", length = 50)
    private String actualValue;

    protected CalendarEvent() {
    }
}