package com.swyp.ploutos.stock.quote.service;

/** 종목 상세 화면의 현재가와 주요 지표를 한 번에 읽는다. */
public interface StockQuoteDetailReader {

    StockQuoteDetail read(Long stockId);
}
