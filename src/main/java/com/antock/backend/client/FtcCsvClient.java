package com.antock.backend.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FtcCsvClient {
    private static final String MAIN_URL = "https://www.ftc.go.kr/www/selectBizCommOpenList.do?key=255";
    
    // 시/도 코드 매핑
    private static final Map<String, String> CITY_CODE_MAP = new HashMap<>();
    
    // 쓰레드 풀 설정
    private static final ExecutorService executorService =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    
    static {
        CITY_CODE_MAP.put("서울특별시", "6110000");
        CITY_CODE_MAP.put("부산광역시", "6260000");
        CITY_CODE_MAP.put("대구광역시", "6270000");
        CITY_CODE_MAP.put("인천광역시", "6280000");
        CITY_CODE_MAP.put("광주광역시", "6290000");
        CITY_CODE_MAP.put("대전광역시", "6300000");
        CITY_CODE_MAP.put("울산광역시", "6310000");
        CITY_CODE_MAP.put("경기도", "6410000");
        CITY_CODE_MAP.put("충청북도", "6430000");
        CITY_CODE_MAP.put("충청남도", "6440000");
        CITY_CODE_MAP.put("전라남도", "6460000");
        CITY_CODE_MAP.put("경상북도", "6470000");
        CITY_CODE_MAP.put("경상남도", "6480000");
        CITY_CODE_MAP.put("제주특별자치도", "6500000");
        CITY_CODE_MAP.put("강원특별자치도", "6530000");
        CITY_CODE_MAP.put("전북특별자치도", "6540000");
        CITY_CODE_MAP.put("세종특별자치시", "5690000");
        CITY_CODE_MAP.put("국외사업자", "9990000");
    }
    
    /**
     * 공정거래위원회 사이트에서 CSV 파일을 다운로드합니다.
     * Selenium을 사용하여 실제 브라우저 동작을 시뮬레이션합니다.
     */
    public InputStream downloadCsvFile(String city, String district) {
        log.info("Downloading CSV file for city: {}, district: {}", city, district);
        WebDriver driver = null;
        
        try {
            // 다운로드 디렉토리 설정
            String downloadDir = System.getProperty("java.io.tmpdir") + "/ftc_downloads";
            File downloadDirFile = new File(downloadDir);
            if (!downloadDirFile.exists()) {
                downloadDirFile.mkdirs();
            } else {
                // 기존 파일 삭제
                File[] existingFiles = downloadDirFile.listFiles();
                if (existingFiles != null) {
                    for (File file : existingFiles) {
                        file.delete();
                    }
                }
            }
            
            log.info("Download directory: {}", downloadDir);
            
            // Chrome 옵션 설정
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // 헤드리스 모드
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            
            // 다운로드 설정
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadDir);
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            prefs.put("safebrowsing.enabled", false);
            options.setExperimentalOption("prefs", prefs);
            
            // WebDriver 초기화
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            
            // 메인 페이지 접속
            driver.get(MAIN_URL);
            log.info("Navigated to main page");
            
            // 시/도 선택
            WebElement citySelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("searchInst1")));
            Select cityDropdown = new Select(citySelect);
            
            // 시/도 이름으로 선택
            boolean cityFound = false;
            for (WebElement option : cityDropdown.getOptions()) {
                if (option.getText().equals(city)) {
                    cityDropdown.selectByVisibleText(city);
                    cityFound = true;
                    log.info("Selected city by text: {}", city);
                    break;
                }
            }
            
            // 이름으로 찾지 못한 경우 코드로 시도
            if (!cityFound) {
                String cityCode = CITY_CODE_MAP.get(city);
                if (cityCode != null) {
                    cityDropdown.selectByValue(cityCode);
                    log.info("Selected city by code: {} ({})", city, cityCode);
                } else {
                    log.error("City not found: {}", city);
                    return null;
                }
            }
            
            // AJAX 로딩 대기
            Thread.sleep(2000);
            
            // 구/군 선택
            WebElement districtSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("searchInst2")));
            Select districtDropdown = new Select(districtSelect);
            
            if (district == null || district.isEmpty() || "전체".equals(district)) {
                // 전체 선택 (첫 번째 옵션)
                districtDropdown.selectByIndex(0);
                log.info("Selected ALL districts");
            } else {
                // 특정 구/군 선택
                boolean found = false;
                for (WebElement option : districtDropdown.getOptions()) {
                    if (option.getText().equals(district)) {
                        districtDropdown.selectByVisibleText(district);
                        found = true;
                        log.info("Selected district: {}", district);
                        break;
                    }
                }
                
                if (!found) {
                    log.warn("District not found: {}, selecting ALL", district);
                    districtDropdown.selectByIndex(0);
                }
            }
            
            // 다운로드 버튼 클릭
            WebElement downloadButton = driver.findElement(By.cssSelector("a.btn.md.primary.ico-down"));
            downloadButton.click();
            log.info("Clicked download button");
            
            // 다운로드 완료 대기
            Thread.sleep(5000);
            
            // 다운로드된 파일 찾기
            File[] files = downloadDirFile.listFiles();
            if (files == null || files.length == 0) {
                log.error("No files downloaded");
                return null;
            }
            
            // 가장 최근 파일 사용
            File downloadedFile = files[0];
            for (File file : files) {
                if (file.lastModified() > downloadedFile.lastModified()) {
                    downloadedFile = file;
                }
            }
            
            log.info("Downloaded file: {}", downloadedFile.getName());
            
            // 파일 인코딩 처리
            try {
                // EUC-KR 인코딩으로 파일 읽기 (한국어 CSV 파일의 일반적인 인코딩)
                FileInputStream fis = new FileInputStream(downloadedFile);
                byte[] fileContent = new byte[(int) downloadedFile.length()];
                fis.read(fileContent);
                fis.close();
                
                // 파일 스트림 반환
                return new ByteArrayInputStream(fileContent);
            } catch (Exception e) {
                log.error("Error reading file with encoding", e);
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error downloading CSV file", e);
            return null;
        } finally {
            // WebDriver 종료
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.error("Error closing WebDriver", e);
                }
            }
        }
    }
    
    /**
     * 공정거래위원회 사이트에서 CSV 파일을 다운로드하고 법인만 필터링합니다.
     * 멀티쓰레드를 활용하여 병렬 처리합니다.
     */
    public List<Map<String, String>> downloadAndFilterCorporations(String city, String district) {
        log.info("국내사업자 CSV 파일 다운로드 시작");
        
        // CSV 파일 다운로드
        InputStream csvStream = downloadCsvFile(city, district);
        if (csvStream == null) {
            log.error("국내사업자 CSV 파일 다운로드 실패");
            return Collections.emptyList();
        }
        
        try {
            // CSV 파일 읽기 (EUC-KR 인코딩 사용)
            BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, "UTF-8"));
            
            // 헤더 읽기
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.error("CSV file is empty");
                return Collections.emptyList();
            }
            
            // 헤더 파싱
            String[] headers = headerLine.split(",");
            log.info("CSV headers: {}", Arrays.toString(headers));
            
            // 사업자등록번호와 법인여부 컬럼 인덱스 찾기
            int businessNumberIndex = -1;
            int corporationTypeIndex = -1;
            
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim();
                if (header.contains("사업자등록번호")) {
                    businessNumberIndex = i;
                } else if (header.contains("법인여부") || header.contains("개인법인구분")) {
                    corporationTypeIndex = i;
                }
            }
            
            if (businessNumberIndex == -1 || corporationTypeIndex == -1) {
                log.error("Required columns not found in CSV");
                return Collections.emptyList();
            }
            
            // 데이터 라인 읽기
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            
            log.info("Total records: {}", lines.size());
            
            // 멀티쓰레드로 법인 필터링
            return filterCorporationsParallel(lines, headers, businessNumberIndex, corporationTypeIndex);
            
        } catch (Exception e) {
            log.error("Error processing CSV file", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 멀티쓰레드를 활용하여 법인만 필터링합니다.
     */
    private List<Map<String, String>> filterCorporationsParallel(
            List<String> lines, String[] headers, int businessNumberIndex, int corporationTypeIndex) {
        
        // 데이터를 청크로 나누기
        int chunkSize = Math.max(1, lines.size() / Runtime.getRuntime().availableProcessors());
        List<List<String>> chunks = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i += chunkSize) {
            chunks.add(lines.subList(i, Math.min(lines.size(), i + chunkSize)));
        }
        
        log.info("Processing data in {} chunks", chunks.size());
        
        // 각 청크를 병렬로 처리
        List<Future<List<Map<String, String>>>> futures = new ArrayList<>();
        
        for (List<String> chunk : chunks) {
            futures.add(executorService.submit(() -> processChunk(chunk, headers, businessNumberIndex, corporationTypeIndex)));
        }
        
        // 결과 수집
        List<Map<String, String>> result = new ArrayList<>();
        
        for (Future<List<Map<String, String>>> future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error processing chunk", e);
            }
        }
        
        log.info("Filtered corporations: {}", result.size());
        return result;
    }
    
    /**
     * 데이터 청크를 처리하여 법인만 필터링합니다.
     */
    private List<Map<String, String>> processChunk(
            List<String> chunk, String[] headers, int businessNumberIndex, int corporationTypeIndex) {
        
        return chunk.stream()
                .map(line -> {
                    try {
                        // CSV 라인 파싱 (쉼표 내의 쉼표 처리)
                        String[] values = parseCSVLine(line);
                        
                        // 법인 여부 확인
                        if (values.length > corporationTypeIndex && 
                            values[corporationTypeIndex].trim().contains("법인")) {
                            
                            // 결과 맵 생성
                            Map<String, String> record = new HashMap<>();
                            for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                                record.put(headers[i].trim(), values[i].trim());
                            }
                            return record;
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing line: {}", line, e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * CSV 라인을 파싱합니다 (쉼표 내의 쉼표 처리).
     */
    private String[] parseCSVLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}