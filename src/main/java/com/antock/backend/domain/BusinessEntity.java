package com.antock.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_entities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessEntity {
    @Id
    private String businessNumber; // 사업자등록번호
    private String mailOrderSalesNumber;          // 통신판매번호
    private String companyName;              // 상호
    private String corporateRegistrationNumber; // 법인등록번호
    private String administrativeCode;       // 행정구역코드
}