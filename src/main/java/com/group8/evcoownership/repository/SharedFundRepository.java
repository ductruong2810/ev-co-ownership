package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.enums.FundType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedFundRepository extends JpaRepository<SharedFund, Long> {

    // KHÔNG DÙNG NỮA (có thể gây IncorrectResultSize nếu 1 group có 2 quỹ)
    // Optional<SharedFund> findByGroup_GroupId(long group_GroupId);

    // List all funds of a group (thường 2 dòng: OPERATING & DEPOSIT_RESERVE)
    List<SharedFund> findAllByGroup_GroupId(Long groupId);

    // Bản phân trang nếu cần
    Page<SharedFund> findAllByGroup_GroupId(Long groupId, Pageable pageable);

    // (Nếu vẫn cần check "group có bất kỳ quỹ nào chưa") — nhưng với mô hình mới
    // nên ưu tiên existsByGroup_GroupIdAndFundType(...)
    boolean existsByGroup_GroupId(Long groupId);

    // Lấy đúng quỹ theo type
    Optional<SharedFund> findByGroup_GroupIdAndFundType(Long groupId, FundType type);

    // Khóa bản ghi quỹ theo type để ghi (tránh race khi cộng/trừ)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SharedFund f where f.group.groupId=:gid and f.fundType=:type")
    Optional<SharedFund> lockByGroupAndType(@Param("gid") Long gid, @Param("type") FundType type);

    boolean existsByGroup_GroupIdAndFundType(Long groupId, FundType type);
}
