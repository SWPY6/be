package com.swyp.ploutos.industry.flow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.industry.flow.IndustryFlows;

public interface IndustryFlowRepository extends JpaRepository<IndustryFlows, Long> {

    List<IndustryFlows> findByCountry(Country country);

    Optional<IndustryFlows> findByIndustryIdAndCountry(Long industryId, Country country);
}
