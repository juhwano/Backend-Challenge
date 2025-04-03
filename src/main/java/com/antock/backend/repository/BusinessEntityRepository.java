package com.antock.backend.repository;

import com.antock.backend.domain.BusinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessEntityRepository extends JpaRepository<BusinessEntity, Long> {
    
    /**
     * 사업자등록번호로 엔티티 존재 여부 확인
     */
    boolean existsByBusinessNumber(String businessNumber);
}