package com.dangdang.domain.myfood.repository;

import com.dangdang.domain.myfood.entity.MyFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MyFoodRepository extends JpaRepository<MyFood, Long> {

    List<MyFood> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);

    // 이름으로 찾기 (식사 내용 검색 F-14에서 「내 음식」 먼저 보여 줄 때도 쓴다)
    List<MyFood> findByUserIdAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(Long userId, String keyword);

    Optional<MyFood> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
