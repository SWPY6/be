package com.swyp.ploutos.news.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.news.RelatedNews;
import com.swyp.ploutos.news.repository.NewsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class JpaNewsReader implements NewsReader {

    private final NewsRepository newsRepository;

    @Override
    public List<RelatedNews> readByStockIds(List<Long> stockIds, LocalDateTime from, LocalDateTime to) {
        // 빈 목록을 그대로 넘기면 `in ()` 이 되어 SQL 문법 오류가 난다.
        // 산업에 매핑된 종목이 아직 없는 경우가 실제로 있다.
        if (stockIds.isEmpty()) {
            return List.of();
        }
        return newsRepository.findRelated(stockIds, from, to);
    }
}
