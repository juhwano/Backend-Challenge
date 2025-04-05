package com.antock.backend.repository;

import com.antock.backend.domain.BusinessEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessEntityRepository extends JpaRepository<BusinessEntity, Long> {
    
    boolean existsByBusinessNumber(String businessNumber);
    
    boolean existsByMailOrderSalesNumber(String mailOrderSalesNumber);
    
    List<BusinessEntity> findByMailOrderSalesNumberIn(List<String> mailOrderSalesNumbers);
}