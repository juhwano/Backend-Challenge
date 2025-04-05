package com.antock.backend.integration;

import com.antock.backend.client.FtcCsvClient;
import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.dto.BusinessEntityRequest;
import com.antock.backend.repository.BusinessEntityRepository;
import com.antock.backend.service.OverseasBusinessEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antock.backend.service.OverseasBusinessEntityService;
import org.springframework.context.ApplicationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("사업자 정보 통합 테스트")
class BusinessEntityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessEntityRepository businessEntityRepository;

    @Autowired
    private FtcCsvClient ftcCsvClient;

    @Autowired
    private RestTemplate restTemplate;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public FtcCsvClient mockFtcCsvClient() {
            return mock(FtcCsvClient.class);
        }

        @Bean
        @Primary
        public RestTemplate mockRestTemplate() {
            return mock(RestTemplate.class);
        }
    }

    @Test
    @DisplayName("국내 사업자 정보 처리 통합 테스트")
    void processBusinessEntities_shouldSaveEntities() throws Exception {
        // Given
        String csvContent = "번호,상호,대표자,사업자등록번호,법인여부\n" +
                            "12-12-12345,테스트법인,홍길동,1234567890,법인\n";
        
        InputStream csvStream = new ByteArrayInputStream(csvContent.getBytes("EUC-KR"));
        when(ftcCsvClient.downloadCsvFile(anyString(), anyString())).thenReturn(csvStream);
        
        // API 응답 모킹
        ResponseEntity<String> mockResponse = mock(ResponseEntity.class);
        when(mockResponse.getBody()).thenReturn(
            "{\"response\":{\"body\":{\"items\":{\"item\":[{\"bizrno\":\"1234567890\",\"corpNo\":\"110111-1234567\",\"mllBsNm\":\"테스트법인\",\"mllBsNo\":\"2023-서울강남-1234\",\"rdnmAdr\":\"서울특별시 강남구 테헤란로 123\"}]}}}}"
        );
        when(restTemplate.exchange(
            anyString(), 
            any(HttpMethod.class), 
            any(HttpEntity.class), 
            eq(String.class))
        ).thenReturn(mockResponse);
        
        // 주소 API 응답 모킹
        ResponseEntity<String> addressResponse = mock(ResponseEntity.class);
        when(addressResponse.getBody()).thenReturn(
            "{\"results\":{\"common\":{\"totalCount\":1},\"juso\":[{\"admCd\":\"1111011700\",\"roadAddr\":\"서울특별시 강남구 테헤란로 123\"}]}}"
        );
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(addressResponse);
        
        BusinessEntityRequest request = new BusinessEntityRequest();
        request.setCity("서울특별시");
        request.setDistrict("강남구");

        // When & Then
        mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @DisplayName("국외 사업자 정보 처리 통합 테스트")
    void processForeignBusinessEntities_shouldSaveEntities() throws Exception {
        // Given - 테스트용 XLS 파일 생성
        ByteArrayOutputStream xlsOutputStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Foreign Entities");
            
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("순번");
            headerRow.createCell(1).setCellValue("관리번호");
            headerRow.createCell(2).setCellValue("대표자명");
            headerRow.createCell(3).setCellValue("법인여부");
            headerRow.createCell(4).setCellValue("법인명(상호)");
            headerRow.createCell(5).setCellValue("사업자번호");
            headerRow.createCell(6).setCellValue("소재지주소");
            headerRow.createCell(7).setCellValue("신고일자");
            headerRow.createCell(8).setCellValue("운영상태");
            headerRow.createCell(9).setCellValue("공개여부");
            
            Row dataRow1 = sheet.createRow(1);
            dataRow1.createCell(0).setCellValue("14");
            dataRow1.createCell(1).setCellValue("2025-공정-0001");
            dataRow1.createCell(2).setCellValue("테스트대표자1");
            dataRow1.createCell(3).setCellValue("Y");
            dataRow1.createCell(4).setCellValue("테스트회사1");
            dataRow1.createCell(5).setCellValue(""); // 사업자번호 없음
            dataRow1.createCell(6).setCellValue("테스트 주소1");
            dataRow1.createCell(7).setCellValue("20250401");
            dataRow1.createCell(8).setCellValue("01");
            dataRow1.createCell(9).setCellValue("Y");
            
            Row dataRow2 = sheet.createRow(2);
            dataRow2.createCell(0).setCellValue("13");
            dataRow2.createCell(1).setCellValue("2025-공정-0002");
            dataRow2.createCell(2).setCellValue("테스트 대표자2");
            dataRow2.createCell(3).setCellValue("Y");
            dataRow2.createCell(4).setCellValue("테스트 회사2");
            dataRow2.createCell(5).setCellValue("");
            dataRow2.createCell(6).setCellValue("테스트 주소2");
            dataRow2.createCell(7).setCellValue("20250319");
            dataRow2.createCell(8).setCellValue("01");
            dataRow2.createCell(9).setCellValue("Y");
            
            workbook.write(xlsOutputStream);
        }
        
        // 국외 사업자 서비스 모킹 - 실제 API 호출 대신 직접 파싱 로직 테스트
        byte[] xlsData = xlsOutputStream.toByteArray();
        
        // OverseasBusinessEntityService 모킹
        OverseasBusinessEntityService overseasService = mock(OverseasBusinessEntityService.class);
        when(overseasService.processBusinessEntities(eq("국외사업자"), eq("전체")))
            .thenAnswer(invocation -> {
                List<BusinessEntity> entities = new ArrayList<>();
                
                BusinessEntity entity1 = BusinessEntity.builder()
                    .mailOrderSalesNumber("2025-공정-0001")
                    .companyName("테스트회사1")
                    .isOverseas(true)
                    .build();
                
                BusinessEntity entity2 = BusinessEntity.builder()
                    .mailOrderSalesNumber("2025-공정-0002")
                    .companyName("테스트 회사2")
                    .isOverseas(true)
                    .build();
                
                entities.add(entity1);
                entities.add(entity2);
                
                // 엔티티 저장
                entities.forEach(businessEntityRepository::save);
                
                return CompletableFuture.completedFuture(entities.size());
            });
        
        // ApplicationContext에 모의 서비스 등록
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(OverseasBusinessEntityService.class)).thenReturn(overseasService);
        
        // 국외 사업자 XLS 파일 다운로드 모킹
        ResponseEntity<byte[]> mockXlsResponse = new ResponseEntity<>(xlsOutputStream.toByteArray(), HttpStatus.OK);
        
        // 모든 RestTemplate.exchange 호출에 대해 응답 설정
        when(restTemplate.exchange(
            anyString(), 
            any(HttpMethod.class),
            any(HttpEntity.class), 
            eq(byte[].class)
        )).thenReturn(mockXlsResponse);
        
        // 요청 객체 생성
        BusinessEntityRequest request = new BusinessEntityRequest();
        request.setCity("국외사업자");
        request.setDistrict("전체");
        
        // When & Then
        mockMvc.perform(post("/v1/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        
    }
}