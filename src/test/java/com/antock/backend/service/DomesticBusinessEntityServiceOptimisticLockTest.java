package com.antock.backend.service;

import com.antock.backend.domain.BusinessEntity;
import com.antock.backend.repository.BusinessEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class DomesticBusinessEntityServiceOptimisticLockTest {

    @Autowired
    private BusinessEntityRepository businessEntityRepository;

    @Test
    public void testOptimisticLocking() throws Exception {
        // 테스트용 엔티티 생성
        BusinessEntity entity = BusinessEntity.builder()
                .businessNumber("123456789")
                .companyName("테스트 회사")
                .mailOrderSalesNumber("TEST-123")
                .build();
        
        // 엔티티 저장
        BusinessEntity savedEntity = businessEntityRepository.save(entity);
        
        // 동시 수정 테스트를 위한 설정
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);
        
        // 여러 스레드에서 동시에 같은 엔티티 수정
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 엔티티 조회
                    BusinessEntity entityToUpdate = businessEntityRepository.findById(savedEntity.getId()).orElseThrow();

                    // 엔티티 수정 - 새 엔티티를 생성하여 업데이트
                    BusinessEntity updatedEntity = BusinessEntity.builder()
                        .id(entityToUpdate.getId())
                        .version(entityToUpdate.getVersion())
                        .businessNumber(entityToUpdate.getBusinessNumber())
                        .mailOrderSalesNumber(entityToUpdate.getMailOrderSalesNumber())
                        .companyName("Updated Company " + index)
                        // 다른 필드들도 복사해야 합니다
                        .build();

                    // 수정된 엔티티 저장 (이전에는 원본 엔티티를 저장하고 있었음)
                    businessEntityRepository.save(updatedEntity);

                    // 스레드 간 경쟁 조건을 만들기 위해 약간의 지연 추가
                    Thread.sleep(10);
                } catch (OptimisticLockingFailureException e) {
                    optimisticLockExceptionCount.incrementAndGet();
                    System.out.println("낙관적 락 예외 발생: " + e.getMessage());
                } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
            });
        }
        
        // 모든 스레드 완료 대기
        latch.await();
        executorService.shutdown();
        
        // 낙관적 락 예외가 발생했는지 확인
        System.out.println("낙관적 락 예외 발생 횟수: " + optimisticLockExceptionCount.get());
        assertTrue(optimisticLockExceptionCount.get() > 0, "낙관적 락 예외가 발생해야 합니다");
    }
}