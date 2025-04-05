package com.antock.backend.service;

import java.util.concurrent.CompletableFuture;

public interface DomesticBusinessEntityService extends BusinessEntityService {
    /**
     * 특정 행정구역의 국내 사업자 정보를 처리합니다.
     * 
     * @param city 시/도
     * @param district 구/군
     * @return 처리된 국내 사업자 수
     */
    @Override
    int processBusinessEntities(String city, String district);
    
    /**
     * 국내 사업자 정보를 비동기적으로 처리합니다.
     * 
     * @param city 시/도
     * @param district 구/군
     * @return 처리된 엔티티 수를 포함하는 CompletableFuture
     */
    @Override
    CompletableFuture<Integer> processBusinessEntitiesAsync(String city, String district);
}