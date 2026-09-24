package com.swyp.ploutos.industry;

import com.swyp.ploutos.common.enums.IndustryCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "industries")
public class Industries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long industryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndustryCode name;

    protected Industries() {
    	
    }
    
}
