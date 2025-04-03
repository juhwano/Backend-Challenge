package com.antock.backend.controller;

import com.antock.backend.dto.BusinessEntityRequest;
import com.antock.backend.dto.BusinessEntityResponse;
import com.antock.backend.service.BusinessEntityService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/business")
@RequiredArgsConstructor
public class BusinessEntityController {
    private final BusinessEntityService businessEntityService;

    @Operation(summary = "통신판매사업자 정보 저장", description = "통신판매번호, 상호, 사업자등록번호, 법인등록번호, 행정구역코드를 데이터베이스에 추가합니다.")
    @PostMapping
    public ResponseEntity<BusinessEntityResponse> addBusinessEntities(@RequestBody BusinessEntityRequest request) {
        int processedCount = businessEntityService.processBusinessEntities(request.getCity(), request.getDistrict());

        BusinessEntityResponse response = BusinessEntityResponse.builder()
            .processedCount(processedCount)
            .message(processedCount > 0 ? 
                    "데이터가 성공적으로 처리되었습니다." : 
                    "데이터 처리 중 오류가 발생했습니다.")
            .build();
        
        return ResponseEntity.ok(response);
    }
}