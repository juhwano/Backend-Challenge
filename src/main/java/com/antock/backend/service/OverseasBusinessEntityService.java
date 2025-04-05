package com.antock.backend.service;

import java.util.concurrent.CompletableFuture;

public interface OverseasBusinessEntityService extends BusinessEntityService {
    /**
     * 국외 사업자 정보를 처리합니다.
     * 
     * @param country 국가 코드 (예: "국외사업자")
     * @param additionalInfo 추가 정보 (예: "전체")
     * @return 처리된 엔티티 수
     */
    @Override
    int processBusinessEntities(String country, String additionalInfo);
    
    /**
     * 국외 사업자 정보를 비동기적으로 처리합니다.
     * 
     * @param country 국가 코드 (예: "국외사업자")
     * @param additionalInfo 추가 정보 (예: "전체")
     * @return 처리된 엔티티 수를 포함하는 CompletableFuture
     */
    CompletableFuture<Integer> processBusinessEntitiesAsync(String country, String additionalInfo);
}