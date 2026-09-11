package com.gachisa.groupbuy.repository;

import com.gachisa.groupbuy.entity.GroupBuy;
import com.gachisa.groupbuy.entity.GroupBuyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long>, JpaSpecificationExecutor<GroupBuy> {

    /**
     * 참여(Participation) 동시성 제어의 핵심 메서드.
     * SELECT ... FOR UPDATE 로 해당 row를 잠근 뒤 반환한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupBuy g WHERE g.id = :id")
    Optional<GroupBuy> findByIdForUpdate(@Param("id") Long id);

    Page<GroupBuy> findByStatus(GroupBuyStatus status, Pageable pageable);

    /** 마감 배치 대상 조회: 모집중인데 마감시각이 지난 공동구매 */
    List<GroupBuy> findByStatusAndDeadlineBefore(GroupBuyStatus status, LocalDateTime now);

    // ---- 검색/필터/정렬 (GB-02) ----
    // 가격 필터/정렬은 정가(basePrice)가 아니라 "할인가(basePrice - basePrice*discountRate)" 기준으로 계산한다.
    // 정렬 방향(마감임박/가격오름/가격내림)마다 ORDER BY가 달라야 해서 쿼리를 3개로 분리했다.
    // 인기순은 참여 인원(currentCount)만으로는 지표가 부족하다고 판단해 뺐다 (조회수 등 별도 데이터 필요).

    String SEARCH_WHERE_CLAUSE =
        "FROM GroupBuy g JOIN g.product p JOIN p.category c " +
            "WHERE g.status = :status " +
            "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:minPrice IS NULL OR (p.basePrice - p.basePrice * g.discountRate) >= :minPrice) " +
            "AND (:maxPrice IS NULL OR (p.basePrice - p.basePrice * g.discountRate) <= :maxPrice) ";

    @Query(value = "SELECT g " + SEARCH_WHERE_CLAUSE + "ORDER BY g.deadline ASC",
        countQuery = "SELECT COUNT(g) " + SEARCH_WHERE_CLAUSE)
    Page<GroupBuy> searchOrderByDeadline(@Param("status") GroupBuyStatus status,
                                         @Param("keyword") String keyword,
                                         @Param("categoryId") Long categoryId,
                                         @Param("minPrice") Integer minPrice,
                                         @Param("maxPrice") Integer maxPrice,
                                         Pageable pageable);

    @Query(value = "SELECT g " + SEARCH_WHERE_CLAUSE + "ORDER BY (p.basePrice - p.basePrice * g.discountRate) ASC",
        countQuery = "SELECT COUNT(g) " + SEARCH_WHERE_CLAUSE)
    Page<GroupBuy> searchOrderByPriceAsc(@Param("status") GroupBuyStatus status,
                                         @Param("keyword") String keyword,
                                         @Param("categoryId") Long categoryId,
                                         @Param("minPrice") Integer minPrice,
                                         @Param("maxPrice") Integer maxPrice,
                                         Pageable pageable);

    @Query(value = "SELECT g " + SEARCH_WHERE_CLAUSE + "ORDER BY (p.basePrice - p.basePrice * g.discountRate) DESC",
        countQuery = "SELECT COUNT(g) " + SEARCH_WHERE_CLAUSE)
    Page<GroupBuy> searchOrderByPriceDesc(@Param("status") GroupBuyStatus status,
                                          @Param("keyword") String keyword,
                                          @Param("categoryId") Long categoryId,
                                          @Param("minPrice") Integer minPrice,
                                          @Param("maxPrice") Integer maxPrice,
                                          Pageable pageable);
}
