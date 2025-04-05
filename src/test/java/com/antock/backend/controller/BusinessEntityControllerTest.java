package com.antock.backend.controller;

import com.antock.backend.dto.BusinessEntityRequest;
import com.antock.backend.service.DomesticBusinessEntityService;
import com.antock.backend.service.DomesticBusinessEntityServiceImpl;
import com.antock.backend.service.OverseasBusinessEntityService;
import com.antock.backend.service.OverseasBusinessEntityServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessEntityController.class)
@Import(BusinessEntityControllerTest.TestConfig.class)
class BusinessEntityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomesticBusinessEntityService domesticBusinessEntityService;

    @Autowired
    private OverseasBusinessEntityService overseasBusinessEntityService;

    @Configuration
    static class TestConfig {
        @Bean
        public DomesticBusinessEntityService domesticBusinessEntityService() {
            return org.mockito.Mockito.mock(DomesticBusinessEntityService.class);
        }

        @Bean
        public OverseasBusinessEntityService overseasBusinessEntityService() {
            return org.mockito.Mockito.mock(OverseasBusinessEntityService.class);
        }
    }

    @Test
    @DisplayName("국내 사업자 정보 처리 요청 테스트")
    void addBusinessEntities_forDomesticEntities_shouldReturnSuccess() throws Exception {
        // Given
        BusinessEntityRequest request = new BusinessEntityRequest();
        request.setCity("서울특별시");
        request.setDistrict("강남구");
        
        when(domesticBusinessEntityService.processBusinessEntities(anyString(), anyString())).thenReturn(10);

        // When & Then
        mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(10))
                .andExpect(jsonPath("$.message").value("데이터가 성공적으로 처리되었습니다."));
    }

    @Test
    @DisplayName("국외 사업자 정보 처리 요청 테스트")
    void addBusinessEntities_forOverseasEntities_shouldReturnSuccess() throws Exception {
        // Given
        BusinessEntityRequest request = new BusinessEntityRequest();
        request.setCity("국외사업자");
        request.setDistrict("전체");
        
        when(overseasBusinessEntityService.processBusinessEntities(anyString(), anyString())).thenReturn(5);

        // When & Then
        mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(5))
                .andExpect(jsonPath("$.message").value("데이터가 성공적으로 처리되었습니다."));
    }

    @Test
    @DisplayName("처리할 데이터가 없는 경우 테스트")
    void addBusinessEntities_whenNoDataProcessed_shouldReturnErrorMessage() throws Exception {
        // Given
        BusinessEntityRequest request = new BusinessEntityRequest();
        request.setCity("서울특별시");
        request.setDistrict("강남구");
        
        when(domesticBusinessEntityService.processBusinessEntities(anyString(), anyString())).thenReturn(0);

        // When & Then
        mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(0))
                .andExpect(jsonPath("$.message").value("데이터 처리 중 오류가 발생했거나 처리할 데이터가 없습니다."));
    }
}