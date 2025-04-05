package com.antock.backend.controller;

import com.antock.backend.dto.BusinessEntityRequest;
import com.antock.backend.dto.BusinessEntityResponse;
import com.antock.backend.service.BusinessEntityService;
import com.antock.backend.service.DomesticBusinessEntityService;
import com.antock.backend.service.OverseasBusinessEntityService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/business")
@RequiredArgsConstructor
public class BusinessEntityController {
    // Change from BusinessEntityService to DomesticBusinessEntityService
    private final DomesticBusinessEntityService domesticBusinessEntityService;
    private final OverseasBusinessEntityService overseasBusinessEntityService;

    @Operation(summary = "통신판매사업자 정보 저장", description = "통신판매번호, 상호, 사업자등록번호, 법인등록번호, 행정구역코드를 데이터베이스에 추가합니다.")
    @PostMapping
    public ResponseEntity<BusinessEntityResponse> addBusinessEntities(@RequestBody BusinessEntityRequest request) {
        log.info("통신판매사업자 정보 저장 요청 - city: {}, district: {}", request.getCity(), request.getDistrict());
        
        int processedCount;
        
        // 국외사업자 요청인 경우 OverseasBusinessEntityService로 라우팅
        if ("국외사업자".equals(request.getCity())) {
            processedCount = overseasBusinessEntityService.processBusinessEntities(
                    request.getCity(), request.getDistrict());
        } else {
            processedCount = domesticBusinessEntityService.processBusinessEntities(
                    request.getCity(), request.getDistrict());
        }
        
        BusinessEntityResponse response = BusinessEntityResponse.builder()
            .processedCount(processedCount)
            .message(processedCount > 0 ? 
                    "데이터가 성공적으로 처리되었습니다." : 
                    "데이터 처리 중 오류가 발생했거나 처리할 데이터가 없습니다.")
            .build();

//        log.debug("처리 완료 - 처리된 엔티티 수: {}", processedCount);
        
        return ResponseEntity.ok(response);
    }
}