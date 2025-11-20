package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Voting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VotingRepository extends JpaRepository<Voting, Long> {
    // Kế thừa JpaRepository<Voting, Long> để có sẵn các hàm CRUD cơ bản
    // như findById, findAll, save, deleteById
    // ========= LẤY DANH SÁCH VOTING THEO GROUP =========
    List<Voting> findByGroupIdOrderByCreatedAtDesc(Long groupId);
    // Lấy tất cả Voting có groupId bằng tham số truyền vào
    // Sắp xếp theo createdAt giảm dần để voting mới nhất nằm trên cùng
    // Dùng cho chức năng hiển thị danh sách tất cả voting trong một group
}