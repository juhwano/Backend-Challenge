package com.antock.backend.repository;

import com.antock.backend.domain.BusinessEntity;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaBusinessEntityStorage implements BusinessEntityStorage {
    
    private final BusinessEntityRepository repository;
    
    public JpaBusinessEntityStorage(BusinessEntityRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public boolean existsByBusinessNumber(String businessNumber) {
        return repository.existsByBusinessNumber(businessNumber);
    }
    
    @Override
    public boolean existsByMailOrderSalesNumber(String mailOrderSalesNumber) {
        return repository.existsByMailOrderSalesNumber(mailOrderSalesNumber);
    }
    
    @Override
    public List<BusinessEntity> findByMailOrderSalesNumberIn(List<String> mailOrderSalesNumbers) {
        return repository.findByMailOrderSalesNumberIn(mailOrderSalesNumbers);
    }
    
    @Override
    public Optional<BusinessEntity> findByBusinessNumber(String businessNumber) {
        return repository.findByBusinessNumber(businessNumber);
    }
    
    @Override
    public BusinessEntity save(BusinessEntity entity) {
        return repository.save(entity);
    }
    
    @Override
    public List<BusinessEntity> saveAll(List<BusinessEntity> entities) {
        return repository.saveAll(entities);
    }
}