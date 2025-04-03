package com.antock.backend.service;

import java.util.concurrent.CompletableFuture;

public interface BusinessEntityService {
    /**
     * 지정된 시/도, 구/군에 대한 법인 정보를 처리하고 저장합니다.
     * 
     * @param city 시/도
     * @param district 구/군
     * @return 처리된 법인 수
     */
    int processBusinessEntities(String city, String district);
    
    /**
     * 비동기로 CSV 파일을 처리합니다. (멀티쓰레드 활용)
     * 
     * @param city 시/도
     * @param district 구/군
     * @return 처리된 법인 수를 포함한 CompletableFuture
     */
    CompletableFuture<Integer> processBusinessEntitiesAsync(String city, String district);
}