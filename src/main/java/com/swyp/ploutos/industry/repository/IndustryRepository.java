package com.swyp.ploutos.industry.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;

public interface IndustryRepository extends JpaRepository<Industries, Long> {

    Optional<Industries> findByName(IndustryCode name);
}
