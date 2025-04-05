package com.antock.backend.client;

import java.io.InputStream;

/**
 * 외부 API 호출을 위한 클라이언트 인터페이스
 */
public interface ApiClient {
    /**
     * API에서 데이터를 바이트 배열로 가져옵니다.
     * 
     * @param apiType API 유형 (예: "국외사업자", "법인등록번호", "행정구역코드" 등)
     * @param params 추가 매개변수
     * @return API 응답 데이터
     */
    byte[] fetchData(String apiType, String... params);
    
    /**
     * API 엔드포인트 URL을 가져옵니다.
     * 
     * @param apiType API 유형
     * @return API 엔드포인트 URL
     */
    String getApiUrl(String apiType);
    
    /**
     * API 엔드포인트 URL을 설정합니다.
     * 
     * @param apiType API 유형
     * @param url 새 API 엔드포인트 URL
     */
    void setApiUrl(String apiType, String url);
}