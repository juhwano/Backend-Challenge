package com.antock.backend.service;

import com.antock.backend.client.FtcCsvClient;
import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.dto.BusinessEntityDto;
import com.antock.backend.repository.BusinessEntityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Update the class declaration to implement DomesticBusinessEntityService
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticBusinessEntityServiceImpl implements DomesticBusinessEntityService {
    private final BusinessEntityRepository businessEntityRepository;
    private final FtcCsvClient ftcCsvClient;
    
    @Override
    @Transactional
    public int processBusinessEntities(String city, String district) {

        try {
            // 1. CSV 파일 다운로드
            InputStream csvStream = ftcCsvClient.downloadCsvFile(city, district);
            if (csvStream == null) {
                log.error("CSV 파일 다운로드 실패");
                return 0;
            }
            
            // 파일 다운로드 성공 로그
            log.info("CSV 파일 다운로드 성공. 다음 프로세스를 진행합니다...");
            
            // 파일 내용 확인 및 HTML 오류 페이지 검사
            byte[] csvBytes = readAllBytes(csvStream);
            if (csvBytes == null || isHtmlContent(new String(csvBytes, StandardCharsets.UTF_8))) {
                log.error("서버에서 HTML 오류 페이지를 반환했습니다. 파일명이 올바른지 확인하세요.");
                return 0;
            }
            
            // 2. CSV 파일에서 법인만 필터링
            List<BusinessEntityDto> corporateEntities = parseCsvAndFilterCorporates(new ByteArrayInputStream(csvBytes));
            if (corporateEntities.isEmpty()) {
                log.info("법인 데이터가 없습니다.");
                return 0;
            }
            
            log.info("법인 필터링 완료. 총 {}개의 법인이 발견되었습니다.", corporateEntities.size());
            
            // 3. API를 통해 데이터 보강 및 엔티티 준비
            List<BusinessEntity> enrichedEntities = enrichAndPrepareEntities(corporateEntities);
            if (enrichedEntities.isEmpty()) {
                log.info("보강된 엔티티가 없습니다.");
                return 0;
            }
            
            log.info("데이터 보강 완료. 총 {}개의 엔티티가 준비되었습니다.", enrichedEntities.size());
            
            // 4. 데이터베이스에 저장 (개별 저장으로 변경)
            int savedCount = 0;
            List<String> failedToSaveBusinessNumbers = new ArrayList<>();
            Map<String, String> failureReasons = new HashMap<>();
            
            for (BusinessEntity entity : enrichedEntities) {
                try {
                    // 중복 체크 한번 더 수행
                    if (businessEntityRepository.existsByBusinessNumber(entity.getBusinessNumber())) {
                        log.debug("저장 직전 중복 체크: 이미 존재하는 사업자등록번호 {}, 건너뜁니다.", entity.getBusinessNumber());
                        failedToSaveBusinessNumbers.add(entity.getBusinessNumber());
                        failureReasons.put(entity.getBusinessNumber(), "DB에 이미 존재");
                        continue;
                    }
                    
                    BusinessEntity savedEntity = businessEntityRepository.save(entity);
                    if (savedEntity != null && savedEntity.getId() != null) {
                        savedCount++;
                        if (savedCount % 50 == 0 || savedCount == 1) {
                            log.info("현재까지 {}개 엔티티 저장 완료", savedCount);
                        }
                    } else {
                        failedToSaveBusinessNumbers.add(entity.getBusinessNumber());
                        failureReasons.put(entity.getBusinessNumber(), "저장 실패 (null 반환)");
                        log.error("엔티티 저장 실패: {}", entity.getBusinessNumber());
                    }
                } catch (Exception e) {
                    failedToSaveBusinessNumbers.add(entity.getBusinessNumber());
                    failureReasons.put(entity.getBusinessNumber(), "예외 발생: " + e.getMessage());
                    log.error("엔티티 저장 중 오류 발생: businessNumber={}, error={}", 
                            entity.getBusinessNumber(), e.getMessage());
                    // 개별 저장이므로 하나의 실패가 전체 트랜잭션을 롤백하지 않음
                }
            }
            
            // 저장에 실패한 사업자등록번호 로깅
            if (!failedToSaveBusinessNumbers.isEmpty()) {
                log.warn("=== DB 저장 실패 목록 ===");
                log.warn("총 {}개 사업자등록번호 DB 저장 실패", failedToSaveBusinessNumbers.size());
                
                // 최대 20개까지만 상세 정보 표시
                int displayCount = Math.min(failedToSaveBusinessNumbers.size(), 20);
                for (int i = 0; i < displayCount; i++) {
                    String businessNumber = failedToSaveBusinessNumbers.get(i);
                    String reason = failureReasons.getOrDefault(businessNumber, "알 수 없는 이유");
                    log.warn("  - 사업자등록번호: {}, 실패 이유: {}", businessNumber, reason);
                }
                
                if (failedToSaveBusinessNumbers.size() > 20) {
                    log.warn("  - 그 외 {}개 생략", failedToSaveBusinessNumbers.size() - 20);
                }
                log.warn("=======================");
            }
            
            // 최종 결과 요약
            log.info("=== 처리 결과 요약 ===");
            log.info("CSV 파일 내 법인 수: {}", corporateEntities.size());
            log.info("API 호출 성공 수: {}", enrichedEntities.size());
            log.info("API 호출 실패 수: {}", corporateEntities.size() - enrichedEntities.size());
            log.info("DB 저장 성공 수: {}", savedCount);
            log.info("DB 저장 실패 수: {}", failedToSaveBusinessNumbers.size());
            log.info("=====================");
            
            return savedCount;
        } catch (Exception e) {
            log.error("비즈니스 엔티티 처리 중 오류 발생", e);
            return 0;
        }
    }
    
    /**
     * 모든 엔티티에 대해 API 호출을 테스트하고 결과를 로깅합니다.
     * 실제 저장은 수행하지 않습니다.
     */
    private void testApiCallsForAllEntities(List<BusinessEntityDto> dtos) {
        // 병렬 처리를 위한 ExecutorService 생성
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            // 각 DTO를 비동기적으로 처리
            List<CompletableFuture<Void>> futures = dtos.stream()
                .map(dto -> CompletableFuture.runAsync(() -> {
                    try {
                        // API를 통해 사업자등록번호로 통신판매번호와 법인등록번호 조회
                        Map<String, String> businessInfo = getBusinessInfoByBusinessNumber(dto.getBusinessNumber());
                        String corporateRegistrationNumber = businessInfo.getOrDefault("corporateRegistrationNumber", "조회 실패");
                        String mailOrderSalesNumber = businessInfo.getOrDefault("mailOrderSalesNumber", "조회 실패");
                        String companyName = businessInfo.getOrDefault("companyName", "조회 실패");
                        String roadAddress = businessInfo.getOrDefault("roadAddress", "조회 실패");
                        String administrativeDistrictCode = businessInfo.getOrDefault("administrativeCode", "조회 실패");
                        
                        // 결과 로깅
                        log.info("API 호출 결과 - 사업자번호: {}, 상호: {}, 통신판매번호: {}, 법인등록번호: {}, 도로명주소: {}, 행정구역코드: {}", 
                                dto.getBusinessNumber(), companyName, mailOrderSalesNumber, 
                                corporateRegistrationNumber, roadAddress, administrativeDistrictCode);
                        
                    } catch (Exception e) {
                        log.error("API 호출 중 오류 발생: {}", dto, e);
                    }
                }, executor))
                .collect(Collectors.toList());
            
            // 모든 Future가 완료될 때까지 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
            log.info("모든 API 호출 테스트 완료");
            
        } catch (Exception e) {
            log.error("API 호출 테스트 중 오류 발생", e);
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * 스트림에서 모든 바이트를 읽어옵니다.
     */
    private byte[] readAllBytes(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("스트림 읽기 오류", e);
            return null;
        }
    }
    
    /**
     * 내용이 HTML인지 확인합니다.
     */
    private boolean isHtmlContent(String content) {
        return content.contains("<!DOCTYPE html>") || content.contains("<html>");
    }
    
    /**
     * CSV 파일을 파싱하고 법인만 필터링합니다.
     * 사업자등록번호(D컬럼)만 추출합니다.
     */
    private List<BusinessEntityDto> parseCsvAndFilterCorporates(InputStream csvStream) {
        List<BusinessEntityDto> corporateEntities = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, "EUC-KR"))) {
            // 헤더 읽기
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.error("CSV 파일에 헤더가 없습니다.");
                return corporateEntities;
            }
            
            // 데이터 라인 읽기
            String line;
            int totalLines = 0;
            int corporateLines = 0;
            int errorLines = 0;
            
            while ((line = reader.readLine()) != null) {
                totalLines++;
                try {
                    // 쉼표로 구분된 데이터 파싱
                    String[] fields = line.split(",");
                    
                    // 필드 수 확인
                    if (fields.length < 5) {
                        log.warn("잘못된 데이터 형식 (필드 수 부족): {}", line);
                        errorLines++;
                        continue;
                    }
                    
                    // 법인여부 확인 (인덱스 4, E컬럼)
                    String corporateType = fields[4].trim();
                    if ("법인".equals(corporateType)) {
                        corporateLines++;
                        // 사업자등록번호 필드 확인 (인덱스 3, D컬럼)
                        if (fields.length < 4 || fields[3].trim().isEmpty()) {
                            log.warn("법인 데이터이지만 사업자등록번호 누락: {}", line);
                            errorLines++;
                            continue;
                        }
                        
                        // 법인인 경우 DTO 생성
                        BusinessEntityDto dto = new BusinessEntityDto();
                        dto.setBusinessNumber(fields[3].trim());  // 사업자등록번호(D컬럼)

                        corporateEntities.add(dto);
//                        log.debug("법인 엔티티 추가: 사업자등록번호={}", dto.getBusinessNumber());
                    }
                } catch (Exception e) {
                    log.warn("라인 파싱 중 오류 발생: {}, 오류: {}", line, e.getMessage());
                    errorLines++;
                }
            }
            
            log.info("CSV 파일 파싱 완료. 총 라인 수: {}, 법인 라인 수: {}, 오류 라인 수: {}, 추출된 법인 수: {}", 
                    totalLines, corporateLines, errorLines, corporateEntities.size());
            
        } catch (Exception e) {
            log.error("CSV 파일 파싱 중 오류 발생: {}", e.getMessage(), e);
        }
        
        return corporateEntities;
    }
    
    /**
     * 필터링된 법인 정보를 외부 API를 통해 보강하고 저장할 엔티티로 변환합니다.
     * 사업자등록번호로 API를 호출하여 통신판매번호, 상호명, 법인등록번호, 행정구역코드를 조회합니다.
     */
    private List<BusinessEntity> enrichAndPrepareEntities(List<BusinessEntityDto> dtos) {
        List<BusinessEntity> result = new ArrayList<>();
        
        // 중복 사업자등록번호 체크를 위한 Set
        Set<String> processedBusinessNumbers = new HashSet<>();
        
        // 실패 원인 추적을 위한 카운터 및 실패한 사업자등록번호 목록
        Map<String, Integer> failureReasons = new HashMap<>();
        Map<String, List<String>> failedBusinessNumbers = new HashMap<>();
        
        failureReasons.put("이미 처리됨", 0);
        failureReasons.put("DB에 이미 존재", 0);
        failureReasons.put("API 결과 없음", 0);
        failureReasons.put("필수 정보 누락", 0);
        failureReasons.put("API 호출 오류", 0);
        
        failedBusinessNumbers.put("이미 처리됨", new ArrayList<>());
        failedBusinessNumbers.put("DB에 이미 존재", new ArrayList<>());
        failedBusinessNumbers.put("API 결과 없음", new ArrayList<>());
        failedBusinessNumbers.put("필수 정보 누락", new ArrayList<>());
        failedBusinessNumbers.put("API 호출 오류", new ArrayList<>());
        
        // 병렬 처리를 위한 ExecutorService 생성
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            // 각 DTO를 비동기적으로 처리
            List<CompletableFuture<BusinessEntity>> futures = dtos.stream()
                .map(dto -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String businessNumber = dto.getBusinessNumber();
                        
                        // 이미 처리한 사업자등록번호인지 확인 (메모리 내 중복 체크)
                        if (processedBusinessNumbers.contains(businessNumber)) {
                            log.debug("이미 처리된 사업자등록번호: {}, 건너뜁니다.", businessNumber);
                            failureReasons.put("이미 처리됨", failureReasons.get("이미 처리됨") + 1);
                            failedBusinessNumbers.get("이미 처리됨").add(businessNumber);
                            return null;
                        }
                        
                        // 데이터베이스에 이미 존재하는지 확인
                        if (businessEntityRepository.existsByBusinessNumber(businessNumber)) {
                            log.debug("데이터베이스에 이미 존재하는 사업자등록번호: {}, 건너뜁니다.", businessNumber);
                            processedBusinessNumbers.add(businessNumber); // 메모리에도 추가
                            failureReasons.put("DB에 이미 존재", failureReasons.get("DB에 이미 존재") + 1);
                            failedBusinessNumbers.get("DB에 이미 존재").add(businessNumber);
                            return null;
                        }
                        
                        // API를 통해 사업자등록번호로 통신판매번호와 법인등록번호 조회
                        Map<String, String> apiResult = getBusinessInfoByBusinessNumber(businessNumber);
                        
                        // API 결과가 없는 경우 건너뜀
                        if (apiResult == null || apiResult.isEmpty()) {
                            log.warn("API 결과 없음: businessNumber={}", businessNumber);
                            failureReasons.put("API 결과 없음", failureReasons.get("API 결과 없음") + 1);
                            failedBusinessNumbers.get("API 결과 없음").add(businessNumber);
                            return null;
                        }
                        
                        // 필수 데이터 추출
                        String mailOrderSalesNumber = apiResult.get("mailOrderSalesNumber");
                        String companyName = apiResult.get("companyName");
                        String corporateRegistrationNumber = apiResult.get("corporateRegistrationNumber");
                        
                        // 필수 정보가 없는 경우 건너뜀
                        if (mailOrderSalesNumber == null || mailOrderSalesNumber.isEmpty() || 
                            companyName == null || companyName.isEmpty() ||
                            corporateRegistrationNumber == null || corporateRegistrationNumber.isEmpty()) {
                            log.warn("필수 정보 누락: businessNumber={}, mailOrderSalesNumber={}, companyName={}, corporateRegistrationNumber={}", 
                                    businessNumber, mailOrderSalesNumber, companyName, corporateRegistrationNumber);
                            failureReasons.put("필수 정보 누락", failureReasons.get("필수 정보 누락") + 1);
                            failedBusinessNumbers.get("필수 정보 누락").add(businessNumber);
                            return null;
                        }
                        
                        // 행정구역코드 가져오기 (API에서 조회한 값 사용)
                        String administrativeDistrictCode = apiResult.get("administrativeCode");
                        
                        // 행정구역코드가 없는 경우
                        if (administrativeDistrictCode == null || administrativeDistrictCode.isEmpty()) {
                            log.warn("행정구역코드 조회 실패, null 값을 사용합니다: businessNumber={}", businessNumber);
                        }
                        
                        // 처리된 사업자등록번호 목록에 추가
                        processedBusinessNumbers.add(businessNumber);
                        
                        // BusinessEntity 객체 생성
                        BusinessEntity entity = BusinessEntity.builder()
                            .mailOrderSalesNumber(mailOrderSalesNumber)
                            .companyName(companyName)
                            .businessNumber(businessNumber)
                            .corporateRegistrationNumber(corporateRegistrationNumber)
                            .administrativeCode(administrativeDistrictCode)
                            .build();
                        
                        return entity;
                    } catch (Exception e) {
                        log.error("엔티티 보강 중 오류 발생: businessNumber={}, error={}", 
                                dto.getBusinessNumber(), e.getMessage());
                        failureReasons.put("API 호출 오류", failureReasons.get("API 호출 오류") + 1);
                        failedBusinessNumbers.get("API 호출 오류").add(dto.getBusinessNumber());
                        return null;
                    }
                }, executor))
                .collect(Collectors.toList());
            
            // 모든 Future가 완료될 때까지 대기
            for (CompletableFuture<BusinessEntity> future : futures) {
                try {
                    BusinessEntity entity = future.get(30, TimeUnit.SECONDS);
                    if (entity != null) {
                        result.add(entity);
                    }
                } catch (Exception e) {
                    log.error("Future 처리 중 오류 발생: {}", e.getMessage());
                }
            }
            
            // 실패 원인 통계 로깅 - 실패한 사업자등록번호 목록 포함
            for (Map.Entry<String, Integer> entry : failureReasons.entrySet()) {
                log.info("=== 실패 원인 통계 ===");
                if (entry.getValue() > 0) {
                    String reason = entry.getKey();
                    int count = entry.getValue();
                    List<String> failedNumbers = failedBusinessNumbers.get(reason);
                    
                    log.info("{}: {}개", reason, count);
                    
                    // 실패한 사업자등록번호 목록 로깅 (최대 20개까지만 표시)
                    if (!failedNumbers.isEmpty()) {
                        int displayCount = Math.min(failedNumbers.size(), 20);
                        String failedList = String.join(", ", failedNumbers.subList(0, displayCount));
                        
                        if (failedNumbers.size() > 20) {
                            failedList += String.format(" 외 %d개", failedNumbers.size() - 20);
                        }
                        
                        log.info("  - 실패한 사업자등록번호: {}", failedList);
                    }
                }
            }
            log.info("===============================");
            
            // 성공 로그는 간결하게
            log.info("총 {}개의 엔티티 보강 완료", result.size());
        } finally {
            executor.shutdown();
        }
        
        return result;
    }
    
    /**
     * 사업자등록번호로 API를 호출하여 통신판매번호와 법인등록번호를 조회합니다.
     * 공공데이터포털 API를 호출합니다.
     */
    private Map<String, String> getBusinessInfoByBusinessNumber(String businessRegistrationNumber) {
        try {
            String apiUrlBase = "https://apis.data.go.kr/1130000/MllBsDtl_2Service/getMllBsInfoDetail_2";
            
            // 이미 인코딩된 서비스 키를 직접 사용
            String encodedServiceKey = "9t5rygA6W%2FqYpdFMUj%2BiLHgDyHYdx5hacXZA01L9BF%2BJkUfYzw%2B14ujB%2BVCyoh3ZGnR8OG2zI40YG%2Bp9kRZ4aA%3D%3D";
            
            // 사업자등록번호에서 하이픈(-) 제거 및 공백 제거
            String formattedBusinessNumber = businessRegistrationNumber.replaceAll("-", "").trim();
            
            // URL 문자열 생성 - 이미 인코딩된 서비스 키 사용
            String urlString = apiUrlBase + 
                    "?serviceKey=" + encodedServiceKey + 
                    "&pageNo=1" + 
                    "&numOfRows=1" + 
                    "&resultType=json" + 
                    "&brno=" + formattedBusinessNumber;
            
            // String URL을 URI 객체로 변환 (추가 인코딩 방지)
            URI uri = new URI(urlString);
            
            // API 호출 - URI 객체 사용
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            Map<String, String> result = new HashMap<>();
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                
                // 응답이 HTML인지 확인 (에러 페이지일 수 있음)
                if (responseBody != null && (responseBody.trim().startsWith("<") || responseBody.contains("<!DOCTYPE html>"))) {
                    log.error("API가 HTML 응답을 반환했습니다. 응답: {}", responseBody.substring(0, Math.min(responseBody.length(), 200)));
                    return result; // 빈 결과 반환
                }
                
                try {
                    // JSON 응답 파싱
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    
                    // 응답 코드 확인
                    JsonNode headerNode = rootNode.path("response").path("header");
                    String resultCode = headerNode.path("resultCode").asText();
                    
                    // 응답 구조 확인 - 일부 API는 response 없이 바로 resultCode를 반환
                    if (resultCode.isEmpty() && rootNode.has("resultCode")) {
                        resultCode = rootNode.path("resultCode").asText();
                    }

                    if ("00".equals(resultCode) || "NORMAL SERVICE".equals(rootNode.path("resultMsg").asText())) {
                        // 성공 응답인 경우 필요한 정보 추출
                        JsonNode itemsNode = rootNode.has("items") ? rootNode.path("items") : 
                                             rootNode.has("response") ? rootNode.path("response").path("body").path("items") : null;
                        
                        JsonNode itemNode = null;
                        if (itemsNode != null) {
                            if (itemsNode.isArray() && itemsNode.size() > 0) {
                                itemNode = itemsNode.get(0);
                            } else {
                                itemNode = itemsNode.path("item");
                            }
                        }
                        
                        if (itemNode != null && !itemNode.isMissingNode()) {
                            // 통신판매번호(prmmiMnno) 추출
                            String mailOrderSalesNumber = itemNode.path("prmmiMnno").asText();
                            if (mailOrderSalesNumber != null && !mailOrderSalesNumber.isEmpty() && !"null".equals(mailOrderSalesNumber)) {
                                result.put("mailOrderSalesNumber", mailOrderSalesNumber);
                            }
                            
                            // 상호명(bzmnNm) 추출 - 기존의 bsshNm 대신 bzmnNm 사용
                            String companyName = itemNode.path("bzmnNm").asText();
                            if (companyName != null && !companyName.isEmpty() && !"null".equals(companyName)) {
                                result.put("companyName", companyName);
                            } else {
                                // 대체 필드로 bsshNm 시도
                                companyName = itemNode.path("bsshNm").asText();
                                if (companyName != null && !companyName.isEmpty() && !"null".equals(companyName)) {
                                    result.put("companyName", companyName);
                                }
                            }
                            
                            // 법인등록번호(crno) 추출
                            String corporateRegistrationNumber = itemNode.path("crno").asText();
                            if (corporateRegistrationNumber != null && !corporateRegistrationNumber.isEmpty() && !"null".equals(corporateRegistrationNumber)) {
                                result.put("corporateRegistrationNumber", corporateRegistrationNumber);
                            }
                            
                            // 도로명주소(rnAddr) 추출 - 행정구역코드 조회에 사용
                            String roadAddress = itemNode.path("rnAddr").asText();
                            if (roadAddress != null && !roadAddress.isEmpty() && !"null".equals(roadAddress) && !"N/A".equals(roadAddress)) {
                                result.put("roadAddress", roadAddress);

                                // 도로명주소로 행정구역코드 조회
                                String admCode = getAdministrativeDistrictCode(roadAddress);
                                if (admCode != null && !admCode.isEmpty()) {
                                    result.put("administrativeCode", admCode);
                                }
                            } else if ("N/A".equals(roadAddress)) {
                                log.warn("도로명주소가 'N/A'로 조회되어 행정구역코드를 조회하지 않습니다: businessNumber={}", businessRegistrationNumber);
                            }
                            
                            if (!result.isEmpty()) {
                                // 상세 성공 로그 제거
                                return result;
                            }
                        }
                    } else {
                        String resultMsg = headerNode.path("resultMsg").asText();
                        log.error("API 오류 응답: {} - {}", resultCode, resultMsg);
                        
                        // API 호출 제한 관련 메시지 확인
                        if (resultMsg.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR") || 
                            resultMsg.contains("일일 제한 횟수") || 
                            resultMsg.contains("호출 제한") || 
                            resultMsg.contains("10,000")) {
                            log.error("=================================================================");
                            log.error("API 호출 제한(10,000회)에 도달했습니다. 내일 다시 시도해주세요.");
                            log.error("오류 메시지: {}", resultMsg);
                            log.error("=================================================================");
                        }
                    }
                } catch (Exception e) {
                    log.error("JSON 파싱 오류: {}", e.getMessage());
                    log.debug("응답 내용: {}", responseBody);
                    
                    if (responseBody != null && 
                        (responseBody.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR") || 
                         responseBody.contains("일일 제한 횟수") || 
                         responseBody.contains("호출 제한") || 
                         responseBody.contains("10,000"))) {
                        log.error("=================================================================");
                        log.error("API 호출 제한(10,000회)에 도달했습니다. 내일 다시 시도해주세요.");
                        log.error("응답 내용에 호출 제한 관련 메시지가 포함되어 있습니다.");
                        log.error("=================================================================");
                    }
                }
            } else {
                log.error("API 호출 실패: {}", response.getStatusCode());
            }
            
            return result; // 빈 결과 반환
        } catch (Exception e) {
            log.error("사업자등록번호로 정보 조회 중 오류 발생: {}, 오류: {}", businessRegistrationNumber, e.getMessage());
            return new HashMap<>(); // 빈 결과 반환
        }
    }
    
    /**
     * 주소로 행정구역코드를 조회합니다.
     * 공공주소 API를 호출합니다.
     */
    private String getAdministrativeDistrictCode(String address) {
        try {
            if (address == null || address.trim().isEmpty() || "N/A".equals(address)) {
                log.warn("행정구역코드를 조회할 수 없습니다.");
                return null;
            }
            
            // API URL 및 파라미터 설정
            String apiUrl = "https://business.juso.go.kr/addrlink/addrLinkApi.do";
            String confmKey = "devU01TX0FVVEgyMDI1MDMyNTE1MTgxMTExNTU3NzE=";
            
            // UriComponentsBuilder를 사용하여 URL 생성
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("currentPage", 1)
                .queryParam("countPerPage", 10)
                .queryParam("keyword", address)
                .queryParam("confmKey", confmKey)
                .queryParam("resultType", "json");
            
            // URI 객체 생성
            URI uri = builder.build().encode().toUri();

            // API 호출
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();

                // 응답이 HTML인지 확인 (에러 페이지일 수 있음)
                if (responseBody != null && (responseBody.trim().startsWith("<") || responseBody.contains("<!DOCTYPE html>"))) {
                    log.error("행정구역코드 API가 HTML 응답을 반환했습니다. 응답: {}", 
                            responseBody.substring(0, Math.min(responseBody.length(), 200)));
                    return null;
                }
                
                try {
                    // JSON 응답 파싱
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    
                    // 결과 코드 확인
                    JsonNode resultsNode = rootNode.path("results");
                    String resultCode = resultsNode.path("common").path("errorCode").asText();
                    
                    if ("0".equals(resultCode)) {
                        // 성공 응답인 경우 행정구역코드 추출
                        JsonNode jusoArray = resultsNode.path("juso");
                        
                        if (jusoArray.isArray() && jusoArray.size() > 0) {
                            // 첫 번째 결과의 행정구역코드(admCd) 추출
                            String admCd = jusoArray.get(0).path("admCd").asText();

                            if ("N/A".equals(admCd)) {
                                log.warn("API에서 'N/A' 행정구역코드가 반환되었습니다.");
                                return null;
                            }
                            
                            if (admCd != null && !admCd.isEmpty()) { return admCd; }
                        } else {
                            log.warn("주소 [{}]에 대한 검색 결과가 없습니다.", address);
                        }
                    } else {
                        String errorMessage = resultsNode.path("common").path("errorMessage").asText();
                        log.error("행정구역코드 API 오류: {} - {}", resultCode, errorMessage);
                    }
                } catch (Exception e) {
                    log.error("행정구역코드 API 응답 파싱 오류: {}", e.getMessage());
                }
            } else {
                log.error("행정구역코드 API 호출 실패: {}", response.getStatusCode());
            }
            
            return null;
        } catch (Exception e) {
            log.error("행정구역코드 조회 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 응답이 HTML 오류 페이지인지 확인합니다.
     */
    private boolean isHtmlErrorPage(InputStream stream) {
        // 스트림을 바이트 배열로 변환하여 내용을 보존
        try {
            byte[] bytes = stream.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            // HTML 오류 페이지인지 확인
            boolean isHtml = content.contains("<!DOCTYPE html>") || content.contains("<html>");
            
            if (isHtml) {
                log.error("HTML 오류 페이지 감지됨. 내용: {}", content);
                
                // 오류 메시지 추출 시도
                if (content.contains("alert(")) {
                    int start = content.indexOf("alert(") + 7; // "alert(" 다음 위치
                    int end = content.indexOf(")", start) - 1; // 닫는 괄호 전 위치
                    if (start > 6 && end > start) {
                        String errorMessage = content.substring(start, end);
                        log.error("서버 오류 메시지: {}", errorMessage);
                    }
                }
            }
            
            // 원본 스트림 복원
            InputStream newStream = new ByteArrayInputStream(bytes);
            
            // 내용 미리보기 (디버깅용)
            previewCsvContent(newStream);
            
            return isHtml;
        } catch (Exception e) {
            log.error("HTML 오류 페이지 확인 중 오류 발생", e);
            return true; // 오류 발생 시 안전하게 오류로 처리
        }
    }
    
    /**
     * CSV 파일의 내용을 미리보기 합니다. (디버깅용)
     */
    private void previewCsvContent(InputStream csvStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            // 헤더 읽기
            String headerLine = reader.readLine();
            if (headerLine != null) {
                log.debug("CSV Header: {}", headerLine);
            }
            
            // 첫 20개 라인 읽기 (더 많은 내용을 확인하기 위해 5에서 20으로 증가)
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) break;
                log.debug("CSV Line {}: {}", i + 1, line);
                content.append(line).append("\n");
            }
            
            // HTML 응답인지 확인
            if (content.toString().contains("<!DOCTYPE html>") || content.toString().contains("<html>")) {
                log.error("Received HTML instead of CSV. This might be an error page or login redirect.");
            }
            
            // 스트림 위치를 처음으로 되돌릴 수 없으므로, 실제 구현에서는 파일을 다시 다운로드하거나
            // 바이트 배열을 저장해두고 새 스트림을 생성해야 합니다.
        } catch (Exception e) {
            log.error("Error previewing CSV content", e);
        }
    }
    
    /**
     * 비동기로 CSV 파일을 처리합니다. (멀티쓰레드 활용)
     * 실제 구현 시 사용할 메서드입니다.
     */
    @Override
    @Async
    public CompletableFuture<Integer> processBusinessEntitiesAsync(String city, String district) {
        int result = processBusinessEntities(city, district);
        return CompletableFuture.completedFuture(result);
    }
}