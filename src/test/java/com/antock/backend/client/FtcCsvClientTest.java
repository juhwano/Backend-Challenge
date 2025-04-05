package com.antock.backend.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FtcCsvClientTest {

    @InjectMocks
    private FtcCsvClient ftcCsvClient;

    @Test
    @DisplayName("CSV 라인 파싱 테스트 - 쉼표 내의 쉼표 처리")
    void parseCSVLine_shouldHandleCommasWithinQuotes() throws Exception {
        // 리플렉션을 사용하여 private 메소드 접근
        java.lang.reflect.Method parseCSVLineMethod = FtcCsvClient.class.getDeclaredMethod("parseCSVLine", String.class);
        parseCSVLineMethod.setAccessible(true);
        
        // 따옴표 안의 쉼표가 있는 경우 테스트
        String line = "1,\"회사명, 주식회사\",홍길동,1234567890,법인";
        String[] result = (String[]) parseCSVLineMethod.invoke(ftcCsvClient, line);
        
        assertEquals(5, result.length);
        assertEquals("회사명, 주식회사", result[1]);
    }

    @Test
    @DisplayName("법인 필터링 테스트")
    void filterCorporations_shouldOnlyReturnCorporateEntities() throws Exception {
        // 샘플 데이터
        List<String> lines = Arrays.asList(
            "1,회사1,홍길동,1111111111,법인",
            "2,회사2,김철수,2222222222,개인",
            "3,회사3,이영희,3333333333,법인"
        );
        
        String[] headers = {"번호", "상호", "대표자", "사업자등록번호", "법인여부"};
        
        // 리플렉션을 사용하여 private 메소드 접근
        java.lang.reflect.Method processChunkMethod = FtcCsvClient.class.getDeclaredMethod(
            "processChunk", List.class, String[].class, int.class, int.class);
        processChunkMethod.setAccessible(true);
        
        // 제네릭 타입 명시하여 unchecked 경고 제거
        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>) processChunkMethod.invoke(
            ftcCsvClient, lines, headers, 3, 4);
        
        assertEquals(2, result.size());
        assertEquals("1111111111", result.get(0).get("사업자등록번호"));
        assertEquals("3333333333", result.get(1).get("사업자등록번호"));
    }
}