package com.antock.backend.service;

import com.antock.backend.client.FtcCsvClient;
import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.dto.BusinessEntityDto;
import com.antock.backend.repository.BusinessEntityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEntityServiceImpl implements BusinessEntityService {
    
    private final BusinessEntityRepository businessEntityRepository;
    private final FtcCsvClient ftcCsvClient;
    
    @Override
    @Transactional
    public int processBusinessEntities(String city, String district) {
        log.info("Processing business entities for city: {}, district: {}", city, district);
        
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
            
            // 3. API 호출 테스트 - 첫 번째 엔티티만 사용하여 API 호출 테스트
            if (!corporateEntities.isEmpty()) {
                BusinessEntityDto testDto = corporateEntities.get(0);
                log.info("API 테스트를 위한 샘플 데이터: {}", testDto);
                
                // 법인등록번호 조회 API 테스트 - getBusinessInfoByBusinessNumber 사용
                Map<String, String> businessInfo = getBusinessInfoByBusinessNumber(testDto.getBusinessNumber());
                String corporateRegistrationNumber = businessInfo.getOrDefault("corporateRegistrationNumber", "조회 실패");
                log.info("법인등록번호 조회 결과: {}", corporateRegistrationNumber);
                
                // 행정구역코드 조회 API 테스트
                String administrativeDistrictCode = getAdministrativeDistrictCode(testDto.getAddress());
                log.info("행정구역코드 조회 결과: {}", administrativeDistrictCode);
                
                // 모든 엔티티에 대해 API 호출 결과 로깅 (실제 저장은 하지 않음)
                testApiCallsForAllEntities(corporateEntities);
                
                return corporateEntities.size();
            }
            
            return 0;
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
                        
                        // 주소 정보는 API에서 가져오므로 CSV에서 추출할 필요 없음
                        
                        corporateEntities.add(dto);
                        log.debug("법인 엔티티 추가: 사업자등록번호={}", dto.getBusinessNumber());
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
     * 사업자등록번호로 API를 호출하여 통신판매번호와 법인등록번호를 조회합니다.
     */
    private List<BusinessEntity> enrichAndPrepareEntities(List<BusinessEntityDto> dtos) {
        List<BusinessEntity> result = new ArrayList<>();
        
        // 병렬 처리를 위한 ExecutorService 생성
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            // 각 DTO를 비동기적으로 처리
            List<CompletableFuture<BusinessEntity>> futures = dtos.stream()
                .map(dto -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // API를 통해 사업자등록번호로 통신판매번호와 법인등록번호 조회
                        Map<String, String> apiResult = getBusinessInfoByBusinessNumber(dto.getBusinessNumber());
                        
                        // API 결과가 없는 경우 건너뜀
                        if (apiResult == null || apiResult.isEmpty()) {
                            log.warn("API 결과 없음: businessNumber={}", dto.getBusinessNumber());
                            return null;
                        }
                        
                        String mailOrderSalesNumber = apiResult.get("mailOrderSalesNumber");
                        String companyName = apiResult.get("companyName");
                        String corporateRegistrationNumber = apiResult.get("corporateRegistrationNumber");
                        
                        // 필수 정보가 없는 경우 건너뜀
                        if (mailOrderSalesNumber == null || corporateRegistrationNumber == null) {
                            log.warn("필수 정보 누락: businessNumber={}", dto.getBusinessNumber());
                            return null;
                        }
                        
                        // 행정구역코드 가져오기 (API에서 조회한 값 사용)
                        String administrativeDistrictCode = apiResult.get("administrativeCode");
                        
                        // 행정구역코드가 없는 경우 임시 코드 생성
                        if (administrativeDistrictCode == null || administrativeDistrictCode.isEmpty()) {
                            administrativeDistrictCode = String.format("%05d", new Random().nextInt(100000));
                            log.warn("행정구역코드 조회 실패, 임시 코드 생성: {}", administrativeDistrictCode);
                        }
                        
                        // BusinessEntity 객체 생성
                        return BusinessEntity.builder()
                            .mailOrderSalesNumber(mailOrderSalesNumber)
                            .companyName(companyName)
                            .businessNumber(dto.getBusinessNumber())
                            .corporateRegistrationNumber(corporateRegistrationNumber)
                            .administrativeCode(administrativeDistrictCode)
                            .build();
                    } catch (Exception e) {
                        log.error("엔티티 보강 중 오류 발생: {}", dto, e);
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
                    log.error("Future 처리 중 오류 발생", e);
                }
            }
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
            
            log.info("API 요청 URL: {}", urlString);
            
            // String URL을 URI 객체로 변환 (추가 인코딩 방지)
            URI uri = new URI(urlString);
            
            // API 호출 - URI 객체 사용
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            Map<String, String> result = new HashMap<>();
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                
                // Log the full response for debugging
                log.debug("API 응답 전체: {}", responseBody);
                
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
                            
                            // 상호명(bsshNm) 추출
                            String companyName = itemNode.path("bsshNm").asText();
                            if (companyName != null && !companyName.isEmpty() && !"null".equals(companyName)) {
                                result.put("companyName", companyName);
                            }
                            
                            // 법인등록번호(crno) 추출
                            String corporateRegistrationNumber = itemNode.path("crno").asText();
                            if (corporateRegistrationNumber != null && !corporateRegistrationNumber.isEmpty() && !"null".equals(corporateRegistrationNumber)) {
                                result.put("corporateRegistrationNumber", corporateRegistrationNumber);
                                log.info("법인등록번호 조회 성공: {}", corporateRegistrationNumber);
                            }
                            
                            // 도로명주소(rnAddr) 추출 - 행정구역코드 조회에 사용
                            String roadAddress = itemNode.path("rnAddr").asText();
                            if (roadAddress != null && !roadAddress.isEmpty() && !"null".equals(roadAddress) && !"N/A".equals(roadAddress)) {
                                result.put("roadAddress", roadAddress);
                                log.info("도로명주소 추출 성공: {}", roadAddress);
                                
                                // 도로명주소로 행정구역코드 조회
                                String admCode = getAdministrativeDistrictCode(roadAddress);
                                if (admCode != null && !admCode.isEmpty()) {
                                    result.put("administrativeCode", admCode);
                                    log.info("행정구역코드 조회 성공: {}", admCode);
                                }
                            }
                            
                            if (!result.isEmpty()) {
                                log.info("사업자등록번호 {}로 조회 성공: 통신판매번호={}, 상호명={}, 법인등록번호={}, 도로명주소={}, 행정구역코드={}", 
                                        businessRegistrationNumber, 
                                        result.getOrDefault("mailOrderSalesNumber", "없음"),
                                        result.getOrDefault("companyName", "없음"),
                                        result.getOrDefault("corporateRegistrationNumber", "없음"),
                                        result.getOrDefault("roadAddress", "없음"),
                                        result.getOrDefault("administrativeCode", "없음"));
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
                    
                    // HTML 응답에서도 호출 제한 관련 메시지 확인
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
            if (address == null || address.trim().isEmpty()) {
                log.warn("주소가 비어있어 행정구역코드를 조회할 수 없습니다.");
                return String.format("%05d", new Random().nextInt(100000)); // 임시 코드 반환
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
            log.info("행정구역코드 조회 API 요청 URL: {}", uri);
            
            // API 호출
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                log.debug("행정구역코드 API 응답: {}", responseBody);
                
                // 응답이 HTML인지 확인 (에러 페이지일 수 있음)
                if (responseBody != null && (responseBody.trim().startsWith("<") || responseBody.contains("<!DOCTYPE html>"))) {
                    log.error("행정구역코드 API가 HTML 응답을 반환했습니다. 응답: {}", 
                            responseBody.substring(0, Math.min(responseBody.length(), 200)));
                    return String.format("%05d", new Random().nextInt(100000)); // 임시 코드 반환
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
                            
                            if (admCd != null && !admCd.isEmpty()) {
                                log.info("주소 [{}]에 대한 행정구역코드 조회 성공: {}", address, admCd);
                                return admCd;
                            }
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
            
            // API 호출 실패 시 임시 코드 반환
            return String.format("%05d", new Random().nextInt(100000));
        } catch (Exception e) {
            log.error("행정구역코드 조회 중 오류 발생: {}", address, e);
            return String.format("%05d", new Random().nextInt(100000)); // 임시 코드 반환
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