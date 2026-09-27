package com.swyp.ploutos.industry;

import com.swyp.ploutos.common.enums.IndustryCode;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
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

    public Industries(IndustryCode name) {
        this.name = name;
    }

    // 호출자가 name().displayName() 으로 두 단계를 타지 않게 한다.
    public String displayName() {
        return name.displayName();
    }

}
