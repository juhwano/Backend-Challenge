package com.antock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEntityRequest {
    private String city;     // 시/도
    private String district; // 구/군
}