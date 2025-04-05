package com.antock.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
@Entity
@Table(name = "business_entity")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BusinessEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;
    
    @Column(name = "mail_order_sales_number", nullable = false)
    private String mailOrderSalesNumber; // 통신판매번호
    
    @Column(name = "company_name", nullable = false)
    private String companyName; // 상호
    
    @Column(name = "business_number", unique = true)
    private String businessNumber; // 사업자등록번호
    
    @Column(name = "corporate_registration_number")
    private String corporateRegistrationNumber; // 법인등록번호
    
    @Column(name = "administrative_code")
    private String administrativeCode; // 행정구역코드

    @Column(name = "is_overseas")
    private boolean isOverseas; // 해외사업자 여부
}