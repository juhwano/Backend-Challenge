package com.antock.backend.repository;

import com.antock.backend.domain.BusinessEntity;
import java.util.List;
import java.util.Optional;

public interface BusinessEntityStorage {
    
    boolean existsByBusinessNumber(String businessNumber);
    
    boolean existsByMailOrderSalesNumber(String mailOrderSalesNumber);
    
    List<BusinessEntity> findByMailOrderSalesNumberIn(List<String> mailOrderSalesNumbers);

    Optional<BusinessEntity> findByBusinessNumber(String businessNumber);
    
    BusinessEntity save(BusinessEntity entity);
    
    List<BusinessEntity> saveAll(List<BusinessEntity> entities);
}