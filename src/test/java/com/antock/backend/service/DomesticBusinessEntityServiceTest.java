package com.antock.backend.service;

import com.antock.backend.client.FtcCsvClient;
import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.repository.BusinessEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("국내 사업자 서비스 CSV 파일 테스트")
class DomesticBusinessEntityServiceTest {

    @Mock
    private FtcCsvClient ftcCsvClient;

    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @Mock
    private RestTemplate restTemplate;

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @InjectMocks
    private DomesticBusinessEntityServiceImpl domesticBusinessEntityService;

    @Test
    @DisplayName("CSV 파일 다운로드 실패 시 0을 반환해야 함")
    void processBusinessEntities_whenCsvDownloadFails_shouldReturnZero() {
        // Given
        when(ftcCsvClient.downloadCsvFile(anyString(), anyString())).thenReturn(null);

        // When
        int result = domesticBusinessEntityService.processBusinessEntities("서울특별시", "강남구");

        // Then
        assertEquals(0, result);
        verify(ftcCsvClient).downloadCsvFile("서울특별시", "강남구");
        verifyNoMoreInteractions(businessEntityRepository);
    }

    @Test
    @DisplayName("CSV 파일에 법인 데이터가 없을 경우 0을 반환해야 함")
    void processBusinessEntities_whenNoCorporateEntities_shouldReturnZero() {
        // Given
        String csvContent = "번호,상호,대표자,사업자등록번호,법인여부\n" +
                            "1,개인사업자1,홍길동,1234567890,개인\n" +
                            "2,개인사업자2,김철수,0987654321,개인\n";
        
        InputStream csvStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        when(ftcCsvClient.downloadCsvFile(anyString(), anyString())).thenReturn(csvStream);

        // When
        int result = domesticBusinessEntityService.processBusinessEntities("서울특별시", "강남구");

        // Then
        assertEquals(0, result);
        verify(ftcCsvClient).downloadCsvFile("서울특별시", "강남구");
        verifyNoMoreInteractions(businessEntityRepository);
    }
}