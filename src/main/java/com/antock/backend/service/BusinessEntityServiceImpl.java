package com.antock.backend.service;

import com.antock.backend.client.FtcCsvClient;
import com.antock.backend.repository.BusinessEntityRepository;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

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
            if (isHtmlErrorPage(csvStream)) {
                log.error("서버에서 HTML 오류 페이지를 반환했습니다. 파일명이 올바른지 확인하세요.");
                return 0;
            }
            
            // 실제 구현에서는 여기서 CSV 파싱 및 DB 저장 로직이 추가될 것입니다.
            // 2. CSV 파일에서 법인만 필터링
            // 3. 법인등록번호 조회
            // 4. 행정구역코드 조회
            // 5. DB에 저장
            
            return 1; // 임시로 1 반환 (실제로는 처리된 엔티티 수 반환)
        } catch (Exception e) {
            log.error("비즈니스 엔티티 처리 중 오류 발생", e);
            return 0;
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
    @Async
    public CompletableFuture<Integer> processBusinessEntitiesAsync(String city, String district) {
        int result = processBusinessEntities(city, district);
        return CompletableFuture.completedFuture(result);
    }
}