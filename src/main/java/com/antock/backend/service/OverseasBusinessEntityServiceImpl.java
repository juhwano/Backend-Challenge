package com.antock.backend.service;

import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.dto.BusinessEntityDto;
import com.antock.backend.repository.BusinessEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OverseasBusinessEntityServiceImpl implements OverseasBusinessEntityService {
    private final BusinessEntityRepository businessEntityRepository;
    private final RestTemplate restTemplate;

    public OverseasBusinessEntityServiceImpl(BusinessEntityRepository businessEntityRepository, RestTemplate restTemplate) {
        this.businessEntityRepository = businessEntityRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public int processBusinessEntities(String country, String additionalInfo) {
        // 국외사업자 요청 확인
        if (!"국외사업자".equals(country)) {
            log.error("지원되지 않는 국가 코드: {}", country);
            return 0;
        }

        log.info("국외사업자 데이터 처리 시작");
        
        try {
            // 국외사업자 XLS 파일 다운로드
            String overseasUrl = "https://www.ftc.go.kr/www/downloadBizOutnatn.do?key=255";
            InputStream xlsStream = downloadOverseasXlsFile(overseasUrl);
            
            if (xlsStream == null) {
                log.error("국외사업자 XLS 파일 다운로드 실패");
                return 0;
            }
            
            log.info("국외사업자 XLS 파일 다운로드 성공. 다음 프로세스를 진행합니다...");
            
            // XLS 파일 파싱하여 국외사업자 정보 추출
            List<BusinessEntityDto> overseasEntities = parseOverseasXls(xlsStream);
            
            if (overseasEntities.isEmpty()) {
                log.info("국외사업자 데이터가 없습니다.");
                return 0;
            }
            
            log.info("국외사업자 파싱 완료. 총 {}개의 국외사업자가 발견되었습니다.", overseasEntities.size());
            
            // 국외사업자 엔티티 준비
            List<BusinessEntity> enrichedEntities = prepareOverseasEntities(overseasEntities);
            
            if (enrichedEntities.isEmpty()) {
                log.info("준비된 국외사업자 엔티티가 없습니다.");
                return 0;
            }
            
            log.info("국외사업자 데이터 준비 완료. 총 {}개의 엔티티가 준비되었습니다.", enrichedEntities.size());
            
            // 데이터베이스에 저장
            int savedCount = saveEntitiesToDatabase(enrichedEntities);
            
            return savedCount;
            
        } catch (Exception e) {
            log.error("국외사업자 엔티티 처리 중 오류 발생", e);
            return 0;
        }
    }
    
    /**
     * 국외사업자 CSV 파일 다운로드
     */
    private InputStream downloadOverseasXlsFile(String url) {
        try {
            log.info("국외사업자 XLS 파일 다운로드 시작: {}", url);

            // 필요한 헤더 추가
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/90.0.4430.212 Safari/537.36");

            String cookieValue = "SDSITE=z9hzmq1guy3B; JSESSIONID=\tp5DDclHYI3LHyep9GlzYyBZ5eX41OErmDRWxynYZ.KFTCEX11;";
            cookieValue = cookieValue.replaceAll("[\\t\\n\\r]+", "").trim();
            headers.set("Cookie", cookieValue);
            headers.set("Referer", "https://www.ftc.go.kr/");
            HttpEntity<String> entity = new HttpEntity<>(headers);
// GET 요청 실행 (exchange 사용)
            ResponseEntity<byte[]> response = restTemplate.exchange(
                "https://www.ftc.go.kr/www/downloadBizOutnatn.do?key=255",
                HttpMethod.GET,
                entity,
                byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("국외사업자 XLS 파일 다운로드 성공. 파일 크기: {} bytes", response.getBody().length);
                return new ByteArrayInputStream(response.getBody());
            } else {
                log.error("국외사업자 XLS 파일 다운로드 실패. 상태 코드: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("국외사업자 XLS 파일 다운로드 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<BusinessEntityDto> parseOverseasXls(InputStream excelStream) {
        List<BusinessEntityDto> overseasEntities = new ArrayList<>();

        try {
            // POI 라이브러리를 사용하여 엑셀 파일 파싱
            org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(excelStream);
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용

            // 헤더 행 읽기 (첫 번째 행)
            org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                log.error("XLS 파일에 헤더 행이 없습니다.");
                return overseasEntities;
            }

            // 헤더 필드 매핑
            Map<String, Integer> fieldIndexMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String headerName = cell.getStringCellValue().trim();
                    fieldIndexMap.put(headerName, i);
                }
            }

//            log.debug("엑셀 헤더 필드: {}", fieldIndexMap.keySet());

            // 필수 필드 인덱스 확인
            Integer managementNoIndex = fieldIndexMap.getOrDefault("관리번호", -1);
            Integer companyNameIndex = fieldIndexMap.getOrDefault("법인명(상호)", -1);
            Integer businessNumberIndex = fieldIndexMap.getOrDefault("사업자번호", -1);
            Integer corporationIndex = fieldIndexMap.getOrDefault("법인여부", -1);
            Integer statusIndex = fieldIndexMap.getOrDefault("운영상태", -1);

            // 필수 필드가 없는 경우 처리
            if (managementNoIndex == -1 || companyNameIndex == -1 || corporationIndex == -1 || statusIndex == -1) {
                log.error("필수 필드가 파일에 없습니다. 관리번호: {}, 법인명: {}, 법인여부: {}, 운영상태: {}",
                    managementNoIndex, companyNameIndex, corporationIndex, statusIndex);
                return overseasEntities;
            }

//            log.debug("필드 인덱스 - 관리번호: {}, 법인명: {}, 사업자번호: {}, 법인여부: {}, 운영상태: {}",
//                managementNoIndex, companyNameIndex, businessNumberIndex, corporationIndex, statusIndex);

            // 데이터 행 읽기 (두 번째 행부터)
            int totalRows = 0;
            int errorRows = 0;
            int filteredOut = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                totalRows++;
                try {
                    // 법인여부 확인 (Y인 경우만 처리)
                    String isCorporation = getCellValueAsString(row.getCell(corporationIndex));
                    if (!"Y".equals(isCorporation)) {
                        filteredOut++;
                        continue;
                    }

                    // 운영상태 확인 (01인 경우만 처리)
                    String operationStatus = getCellValueAsString(row.getCell(statusIndex));
                    if (!"01".equals(operationStatus)) {
                        filteredOut++;
                        continue;
                    }

                    // 필수 필드 값 추출
                    String managementNo = getCellValueAsString(row.getCell(managementNoIndex));
                    String companyName = getCellValueAsString(row.getCell(companyNameIndex));

                    // 필수 정보 확인
                    if (managementNo.isEmpty() || companyName.isEmpty()) {
                        log.warn("필수 정보 누락 (행 {}): 관리번호={}, 법인명={}",
                            rowIndex, managementNo, companyName);
                        errorRows++;
                        continue;
                    }

                    // 국외사업자 DTO 생성
                    BusinessEntityDto dto = new BusinessEntityDto();
                    dto.setMailOrderSalesNumber(managementNo);  // 관리번호
                    dto.setCompanyName(companyName);          // 법인명(상호)

                    // 사업자번호 (있는 경우)
                    if (businessNumberIndex != -1) {
                        String businessNumber = getCellValueAsString(row.getCell(businessNumberIndex));

                        if (!businessNumber.isEmpty()) {
                            dto.setBusinessNumber(businessNumber);
                        }
                    }

                    overseasEntities.add(dto);

                    // 처음 몇 개의 엔티티 샘플 상세 로깅
//                    if (overseasEntities.size() <= 5) {
//                        log.debug("국외사업자 엔티티 추가 (샘플): 통신판매번호={}, 상호명={}, 사업자번호={}",
//                            dto.getMailOrderSalesNumber(), dto.getCompanyName(), dto.getBusinessNumber());
//                    }

                } catch (Exception e) {
                    log.warn("행 파싱 중 오류 발생 (행 {}): 오류: {}", rowIndex, e.getMessage());
                    errorRows++;
                }
            }

            log.info("국외사업자 XLS 파일 파싱 완료. 총 행 수: {}, 오류 행 수: {}, 필터링된 행 수: {}, 추출된 국외사업자 수: {}",
                totalRows, errorRows, filteredOut, overseasEntities.size());

            workbook.close();

        } catch (Exception e) {
            log.error("국외사업자 XLS 파일 파싱 중 오류 발생: {}", e.getMessage(), e);
        }

        return overseasEntities;
    }

    /**
     * 셀 값을 문자열로 안전하게 추출
     */
    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        // 숫자를 문자열로 변환 (소수점 제거)
                        double numValue = cell.getNumericCellValue();
                        if (numValue == Math.floor(numValue)) {
                            return String.format("%.0f", numValue);
                        } else {
                            return String.valueOf(numValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception e) {
                        try {
                            return String.valueOf(cell.getNumericCellValue());
                        } catch (Exception ex) {
                            return "";
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            log.warn("셀 값 추출 중 오류: {}", e.getMessage());
            return "";
        }
    }
    /**
     * 국외사업자 엔티티 준비
     */
    private List<BusinessEntity> prepareOverseasEntities(List<BusinessEntityDto> dtos) {
        List<BusinessEntity> result = new ArrayList<>();
        
        // 실패 원인 추적을 위한 카운터 및 실패한 통신판매번호 목록
        Map<String, Integer> failureReasons = new HashMap<>();
        Map<String, List<String>> failedEntities = new HashMap<>();
        
        failureReasons.put("DB에 이미 존재", 0);
        failureReasons.put("필수 정보 누락", 0);
        
        failedEntities.put("DB에 이미 존재", new ArrayList<>());
        failedEntities.put("필수 정보 누락", new ArrayList<>());
        
        for (BusinessEntityDto dto : dtos) {
            try {
                String mailOrderSalesNumber = dto.getMailOrderSalesNumber();
                String companyName = dto.getCompanyName();
                
                // 필수 정보 확인
                if (mailOrderSalesNumber == null || mailOrderSalesNumber.isEmpty() ||
                    companyName == null || companyName.isEmpty()) {
                    log.warn("필수 정보 누락: mailOrderSalesNumber={}, companyName={}", 
                            mailOrderSalesNumber, companyName);
                    failureReasons.put("필수 정보 누락", failureReasons.get("필수 정보 누락") + 1);
                    failedEntities.get("필수 정보 누락").add(mailOrderSalesNumber != null ? 
                            mailOrderSalesNumber : "unknown");
                    continue;
                }
                
                // 데이터베이스에 이미 존재하는지 확인 (통신판매번호 기준)
                if (businessEntityRepository.existsByMailOrderSalesNumber(mailOrderSalesNumber)) {
                    log.debug("데이터베이스에 이미 존재하는 통신판매번호: {}, 건너뜁니다.", mailOrderSalesNumber);
                    failureReasons.put("DB에 이미 존재", failureReasons.get("DB에 이미 존재") + 1);
                    failedEntities.get("DB에 이미 존재").add(mailOrderSalesNumber);
                    continue;
                }
                
                // 사업자등록번호 처리
                String businessNumber = dto.getBusinessNumber();
                if (businessNumber == null || businessNumber.isEmpty()) {
                    businessNumber = null;
                }
                
                // BusinessEntity 객체 생성
                BusinessEntity entity = BusinessEntity.builder()
                    .mailOrderSalesNumber(mailOrderSalesNumber)
                    .companyName(companyName)
                    .businessNumber(businessNumber)
                    .corporateRegistrationNumber(null)  // 국외사업자는 법인등록번호 없음
                    .administrativeCode(null)  // 국외사업자는 행정구역코드 없음
                    .isOverseas(true)  // 국외사업자 표시
                    .build();
                
//                log.debug("국외사업자 엔티티 생성 완료: {}", entity);
                result.add(entity);
                
            } catch (Exception e) {
                log.error("국외사업자 엔티티 준비 중 오류 발생: mailOrderSalesNumber={}, error={}", 
                        dto.getMailOrderSalesNumber(), e.getMessage());
            }
        }
        
        for (Map.Entry<String, Integer> entry : failureReasons.entrySet()) {
            if (entry.getValue() > 0) {
                log.info("{}: {}개", entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * 엔티티를 데이터베이스에 저장
     */
    private int saveEntitiesToDatabase(List<BusinessEntity> entities) {
        int successCount = 0;
        int failCount = 0;
        Map<String, String> failureReasons = new HashMap<>();
        
        for (BusinessEntity entity : entities) {
            try {
                // 저장 직전 중복 체크 (동시성 문제 방지)
                if (businessEntityRepository.existsByMailOrderSalesNumber(entity.getMailOrderSalesNumber())) {
                    log.debug("저장 직전 중복 체크: 이미 존재하는 통신판매번호 {}, 건너뜁니다.", entity.getMailOrderSalesNumber());
                    failCount++;
                    failureReasons.put(entity.getMailOrderSalesNumber(), "DB에 이미 존재");
                    continue;
                }
                
                BusinessEntity savedEntity = businessEntityRepository.save(entity);
                
                if (savedEntity != null && savedEntity.getId() != null) {
                    successCount++;
                    
                    // 로깅 간격 조절 (100개마다 로그)
                    if (successCount % 100 == 0 || successCount == 1) {
                        log.info("현재까지 {}개 국외사업자 엔티티 저장 완료", successCount);
                    }
                } else {
                    log.warn("저장 실패 (null 반환)");
                    failCount++;
                    failureReasons.put(entity.getMailOrderSalesNumber(), "저장 실패");
                }
            } catch (Exception e) {
                log.error("국외사업자 엔티티 저장 중 오류 발생: mailOrderSalesNumber={}, error={}", 
                        entity.getMailOrderSalesNumber(), e.getMessage());
                failCount++;
                failureReasons.put(entity.getMailOrderSalesNumber(), e.getMessage());
            }
        }
        
        // 저장 결과 로깅
        logSaveResults(successCount, failCount, entities, failureReasons);
        
        return successCount;
    }
    
    /**
     * 저장 결과 로깅
     */
    private void logSaveResults(int successCount, int failCount, List<BusinessEntity> entities, Map<String, String> failureReasons) {
        if (failCount > 0) {
            log.warn("총 {}개 통신판매번호 DB 저장 실패", failCount);
            
            // 실패 원인별 그룹화
            Map<String, List<String>> failuresByReason = new HashMap<>();
            failureReasons.forEach((mailOrderSalesNumber, reason) -> {
                failuresByReason.computeIfAbsent(reason, k -> new ArrayList<>()).add(mailOrderSalesNumber);
            });
            
            // 실패 원인별 로깅 (최대 10개까지만 표시)
            failuresByReason.forEach((reason, mailOrderSalesNumbers) -> {
                int displayCount = Math.min(10, mailOrderSalesNumbers.size());
                for (int i = 0; i < displayCount; i++) {
                    log.warn("  - 통신판매번호: {}, 실패 이유: {}", 
                            mailOrderSalesNumbers.get(i), 
                            reason != null ? reason : "알 수 없는 이유");
                }
                if (mailOrderSalesNumbers.size() > 10) {
                    log.warn("  - 그 외 {}개 생략", mailOrderSalesNumbers.size() - 10);
                }
            });
        }
        

        log.info("=== 국외사업자 처리 결과 요약 ===");
        log.info("총 엔티티 수: {}", entities.size());
        log.info("DB 저장 성공 수: {}", successCount);
        log.info("DB 저장 실패 수: {}", failCount);
        log.info("=====================");
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Integer> processBusinessEntitiesAsync(String country, String additionalInfo) {
        return CompletableFuture.supplyAsync(() -> processBusinessEntities(country, additionalInfo));
    }
}