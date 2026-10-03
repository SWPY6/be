package com.swyp.ploutos.industry.flow.service;

import java.util.List;

import com.swyp.ploutos.industry.flow.IndustryCard.Direction;
import com.swyp.ploutos.industry.flow.IndustryCard.SelectedBy;
import com.swyp.ploutos.industry.flow.RankedIndustryFlow;
import com.swyp.ploutos.news.RelatedNews;

/**
 * 핵심 뉴스 카드 한 장에 필요한 모든 값. 선정 결과에 관련 뉴스를 붙인 것이다.
 *
 * @param news 관련 뉴스. 없으면 빈 목록 — 화면은 이 영역을 생략한다
 */
public record IndustryNewsDetail(
        RankedIndustryFlow flow,
        Direction direction,
        SelectedBy selectedBy,
        List<RelatedNews> news
) {
}
