package com.antock.backend.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class RestApiClient implements ApiClient {
    
    private final RestTemplate restTemplate;
    private final Map<String, String> apiUrls = new HashMap<>();
    
    @Autowired
    public RestApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // 기본 API URL 설정
        apiUrls.put("국외사업자", "https://api.example.com/overseas-business");
        apiUrls.put("법인등록번호", "https://api.example.com/corporate-registration");
        apiUrls.put("행정구역코드", "https://api.example.com/administrative-district");
    }
    
    @Override
    public byte[] fetchData(String apiType, String... params) {
        String url = getApiUrl(apiType);
        if (url == null) {
            throw new IllegalArgumentException("지원되지 않는 API 유형: " + apiType);
        }
        
        // 필요한 경우 URL에 매개변수 추가
        if (params != null && params.length > 0) {
            url = String.format(url, (Object[]) params);
        }
        
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, byte[].class);
            
        return response.getBody();
    }
    
    @Override
    public String getApiUrl(String apiType) {
        return apiUrls.get(apiType);
    }
    
    @Override
    public void setApiUrl(String apiType, String url) {
        apiUrls.put(apiType, url);
    }
}