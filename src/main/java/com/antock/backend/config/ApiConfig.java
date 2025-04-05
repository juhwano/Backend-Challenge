package com.antock.backend.config;

import com.antock.backend.client.ApiClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Configuration
public class ApiConfig {

    private final ApiClient apiClient;
    
    @Value("${api.urls.overseas-business}")
    private String overseasBusinessUrl;
    
    @Value("${api.urls.corporate-registration}")
    private String corporateRegistrationUrl;
    
    @Value("${api.urls.administrative-district}")
    private String administrativeDistrictUrl;
    
    @Autowired
    public ApiConfig(ApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    @PostConstruct
    public void initApiUrls() {
        apiClient.setApiUrl("국외사업자", overseasBusinessUrl);
        apiClient.setApiUrl("법인등록번호", corporateRegistrationUrl);
        apiClient.setApiUrl("행정구역코드", administrativeDistrictUrl);
    }
}