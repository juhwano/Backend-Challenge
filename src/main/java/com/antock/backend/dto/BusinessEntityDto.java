package com.antock.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEntityDto {
    private String mailOrderSalesNumber;
    private String companyName;
    private String businessNumber;
    private String address;
}