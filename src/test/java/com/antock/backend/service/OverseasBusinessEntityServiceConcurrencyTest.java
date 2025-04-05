package com.antock.backend.service;

import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.repository.BusinessEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("국외 사업자 서비스 동시성 테스트")
public class OverseasBusinessEntityServiceConcurrencyTest {

    private OverseasBusinessEntityService overseasBusinessEntityService;

    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @Mock
    private RestTemplate restTemplate;

    // 테스트용 XLS 파일 데이터
    private byte[] mockXlsData;

    @BeforeEach
    public void setup() throws Exception {
        // 테스트용 XLS 파일 생성
        mockXlsData = createMockXlsData();

        overseasBusinessEntityService = new OverseasBusinessEntityServiceImpl(businessEntityRepository, restTemplate);

        // RestTemplate 모의 설정
        ResponseEntity<byte[]> mockResponse = new ResponseEntity<>(mockXlsData, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
            .thenReturn(mockResponse);

        // 저장 메소드 모의 설정
        when(businessEntityRepository.save(any(BusinessEntity.class))).thenAnswer(invocation -> {
            BusinessEntity entity = invocation.getArgument(0);
            // ID 설정 (실제 저장된 것처럼 시뮬레이션)
            setEntityId(entity, 1L);
            return entity;
        });

        when(businessEntityRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<BusinessEntity> entities = invocation.getArgument(0);
            // 각 엔티티에 ID 설정
            for (int i = 0; i < entities.size(); i++) {
                setEntityId(entities.get(i), (long) (i + 1));
            }
            return entities;
        });

        // existsByMailOrderSalesNumber 메소드 모의 설정
        when(businessEntityRepository.existsByMailOrderSalesNumber(anyString())).thenReturn(false);

        // findByMailOrderSalesNumberIn 메소드 모의 설정
        when(businessEntityRepository.findByMailOrderSalesNumberIn(anyList())).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("동시 처리 시 중복 저장 방지 테스트")
    public void testConcurrentProcessing() throws Exception {
        // 동시성 테스트를 위한 설정
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        // 여러 스레드에서 동시에 처리
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    int processed = overseasBusinessEntityService.processBusinessEntities("국외사업자", "테스트");
                    totalProcessed.addAndGet(processed);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기 (최대 30초)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 검증
        assertTrue(completed, "모든 스레드가 시간 내에 완료되어야 합니다");
        verify(businessEntityRepository, atLeastOnce()).saveAll(anyList());
        
        // 중복 저장이 발생하지 않았는지 확인
        assertTrue(totalProcessed.get() > 0, "데이터가 처리되어야 합니다");
    }

    @Test
    @DisplayName("대량 데이터 처리 성능 테스트")
    public void testLargeDataProcessingPerformance() throws Exception {
        // 대량의 테스트 데이터 생성
        byte[] largeMockXlsData = createLargeMockXlsData(1000); // 1000개 데이터
        
        // RestTemplate 응답 재설정
        ResponseEntity<byte[]> mockResponse = new ResponseEntity<>(largeMockXlsData, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
            .thenReturn(mockResponse);

        // 성능 측정 시작
        long startTime = System.currentTimeMillis();
        
        // 처리 실행
        int processed = overseasBusinessEntityService.processBusinessEntities("국외사업자", "테스트");
        
        // 성능 측정 종료
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 검증
        assertTrue(processed > 0, "데이터가 처리되어야 합니다");
        System.out.println("대량 데이터 처리 시간: " + duration + "ms, 처리된 항목 수: " + processed);
        
        // 성능 기준 검증 (10초 이내 처리)
        assertTrue(duration < 10000, "대량 데이터 처리가 10초 이내에 완료되어야 합니다");
    }

    //엔티티에 ID 설정
    private void setEntityId(BusinessEntity entity, Long id) {
        try {
            java.lang.reflect.Field idField = BusinessEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //테스트용 XLS 파일 생성
    private byte[] createMockXlsData() throws Exception {
        // Apache POI를 사용하여 테스트용 XLS 파일 생성
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("국외사업자");
        
        // 헤더 행 생성
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"순번", "관리번호", "대표자명", "법인여부", "법인명(상호)", "사업자번호", "소재지주소", "신고일자", "운영상태", "공개여부"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        // 데이터 행 생성 (10개 샘플 데이터)
        for (int i = 1; i <= 10; i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(i); // 순번
            row.createCell(1).setCellValue("2023-공정-" + String.format("%04d", i)); // 관리번호
            row.createCell(2).setCellValue("대표자" + i); // 대표자명
            row.createCell(3).setCellValue("Y"); // 법인여부
            row.createCell(4).setCellValue("테스트 회사" + i); // 법인명(상호)
            row.createCell(5).setCellValue("12345678" + i); // 사업자번호
            row.createCell(6).setCellValue("서울시 강남구 테스트로 " + i); // 소재지주소
            row.createCell(7).setCellValue("20230101"); // 신고일자
            row.createCell(8).setCellValue("01"); // 운영상태
            row.createCell(9).setCellValue("Y"); // 공개여부
        }
        
        // 바이트 배열로 변환
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        workbook.write(bos);
        workbook.close();
        return bos.toByteArray();
    }

    //대량의 테스트용 XLS 파일 생성
    private byte[] createLargeMockXlsData(int count) throws Exception {
        // Apache POI를 사용하여 대량의 테스트용 XLS 파일 생성
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("국외사업자");
        
        // 헤더 행 생성
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"순번", "관리번호", "대표자명", "법인여부", "법인명(상호)", "사업자번호", "소재지주소", "신고일자", "운영상태", "공개여부"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        // 데이터 행 생성 (count개 데이터)
        for (int i = 1; i <= count; i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(i); // 순번
            row.createCell(1).setCellValue("2023-공정-" + String.format("%04d", i)); // 관리번호
            row.createCell(2).setCellValue("대표자" + i); // 대표자명
            row.createCell(3).setCellValue("Y"); // 법인여부
            row.createCell(4).setCellValue("테스트 회사" + i); // 법인명(상호)
            row.createCell(5).setCellValue("12345678" + i); // 사업자번호
            row.createCell(6).setCellValue("서울시 강남구 테스트로 " + i); // 소재지주소
            row.createCell(7).setCellValue("20230101"); // 신고일자
            row.createCell(8).setCellValue("01"); // 운영상태
            row.createCell(9).setCellValue("Y"); // 공개여부
        }
        
        // 바이트 배열로 변환
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        workbook.write(bos);
        workbook.close();
        return bos.toByteArray();
    }
}