package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.VotingService;
import com.group8.evcoownership.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/votings")
@RequiredArgsConstructor
@Tag(name = "Voting", description = "Voting management APIs")
public class VotingController {

    private final VotingService votingService;
    // Service chứa logic nghiệp vụ cho voting: tạo phiếu bầu, lấy danh sách, chi tiết, xử lý bỏ phiếu,...

    private final JwtUtil jwtUtils;
    // Tiện ích làm việc với JWT token, ví dụ: lấy userId từ token để xác định user hiện tại.


    // ================== TẠO PHIẾU BẦU ==================
    @PostMapping
    @Operation(summary = "Tạo phiếu bầu mới")
    public ResponseEntity<VotingResponseDTO> createVoting( // Trả về ResponseEntity bọc VotingResponseDTO
                                                           // để chủ động set status code và body
                                                           @Valid @RequestBody CreateVotingWithGroupRequestDTO request,
                                                           // @RequestBody: bind JSON trong body request thành object request
                                                           // @Valid: kích hoạt validate theo các annotation trong DTO
                                                           // CreateVotingWithGroupRequestDTO: DTO nhận dữ liệu từ client, có cả groupId
                                                           @RequestHeader("Authorization") String token) {
        // Lấy chuỗi token từ header Authorization (dạng "Bearer <jwt-token>")

        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        // Bỏ đi "Bearer " (7 ký tự đầu) để lấy phần JWT thật sự, sau đó gọi JwtUtil để lấy userId từ token
        CreateVotingRequestDTO votingRequest = new CreateVotingRequestDTO();  // Tạo DTO chuyên dùng cho thông
        // tin nội dung của voting (không bao gồm groupId)
        votingRequest.setTitle(request.getTitle());
        votingRequest.setDescription(request.getDescription());
        votingRequest.setVotingType(request.getVotingType());
        votingRequest.setOptions(request.getOptions());
        votingRequest.setDeadline(request.getDeadline());
        votingRequest.setEstimatedAmount(request.getEstimatedAmount());
        votingRequest.setRelatedExpenseId(request.getRelatedExpenseId());
        VotingResponseDTO response = votingService.createVoting(request.getGroupId(), votingRequest, userId);
        // Gọi service để:
        // - Kiểm tra userId có thuộc groupId trong request không
        // - Kiểm tra quyền tạo voting
        // - Lưu voting + options vào database
        // - Trả về thông tin voting mới tạo dưới dạng VotingResponseDTO

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
        // Trả về HTTP 201 CREATED cùng với dữ liệu voting mới tạo trong body
    }


    // ================== LẤY DANH SÁCH VOTING TRONG NHÓM ==================

    @GetMapping
    @Operation(summary = "Nhận tất cả các phiếu bầu trong nhóm")
    public ResponseEntity<List<VotingResponseDTO>> getGroupVotings(
            // Trả về danh sách các VotingResponseDTO trong ResponseEntity.
            @RequestParam Long groupId,
            // Lấy groupId từ query string
            @RequestHeader("Authorization") String token) {
        // Lấy token từ header Authorization để biết user đang gọi API là ai
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        // Lấy userId từ JWT token (sau khi cắt bỏ "Bearer ")
        List<VotingResponseDTO> votings = votingService.getGroupVotings(groupId, userId);
        // Gọi service:
        // - Kiểm tra user có thuộc group này không
        // - Lấy tất cả voting của group
        // - Trả về dạng DTO để gửi lại client
        return ResponseEntity.ok(votings);
        // Trả về HTTP 200 OK cùng với danh sách voting trong body
    }


    // ================== LẤY CHI TIẾT 1 VOTING ==================

    @GetMapping("/{votingId}")
    // HTTP GET /api/votings/{votingId} – dùng path variable để chỉ rõ phiếu bầu cụ thể

    @Operation(summary = "Nhận chi tiết bỏ phiếu")
    // Mô tả endpoint dùng để xem chi tiết một phiếu bầu (thông tin, options, trạng thái,...).

    public ResponseEntity<VotingResponseDTO> getVotingDetail(
            @PathVariable Long votingId,
            // Lấy votingId từ URL, ví dụ: /api/votings/10 -> votingId = 10

            @RequestHeader("Authorization") String token) {
        // Lấy token để xác định user và kiểm tra quyền xem voting

        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        // Lấy userId từ token JWT

        VotingResponseDTO voting = votingService.getVotingDetail(votingId, userId);
        // Gọi service:
        // - Kiểm tra user có quyền xem voting này không
        // - Lấy dữ liệu voting (options, kết quả hiện tại nếu có)
        // - Map sang VotingResponseDTO

        return ResponseEntity.ok(voting);
        // Trả về HTTP 200 OK với body là thông tin chi tiết voting
    }


    // ================== BỎ PHIẾU ==================

    @PostMapping("/vote")
    @Operation(summary = "Bỏ phiếu")
    public ResponseEntity<VotingResponseDTO> vote(
            @Valid @RequestBody VoteWithIdsRequestDTO request,
            // Nhận JSON body gồm:
            // - votingId: ID của phiếu bầu
            // - selectedOption: lựa chọn mà user đã chọn
            // @Valid để validate theo các rule trong VoteWithIdsRequestDTO

            @RequestHeader("Authorization") String token) {
        // Lấy JWT từ header Authorization để biết ai đang bỏ phiếu

        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        // Lấy userId từ token, dùng để đảm bảo mỗi user chỉ vote 1 lần, đúng group
        VoteRequestDTO voteRequest = new VoteRequestDTO();
        // Tạo DTO chuyên cho nội dung vote (không chứa votingId vì votingId truyền riêng khi gọi service)
        voteRequest.setSelectedOption(request.getSelectedOption());
        // Gán option mà user chọn từ request vào DTO
        VotingResponseDTO response = votingService.vote(request.getVotingId(), voteRequest, userId);
        // Gọi service:
        // - Kiểm tra user có quyền vote ở voting này không
        // - Kiểm tra voting còn mở (chưa hết hạn, chưa bị đóng)
        // - Lưu lựa chọn của user, cập nhật kết quả nếu cần
        // - Trả về VotingResponseDTO thể hiện trạng thái/vote hiện tại
        return ResponseEntity.ok(response);
        // Trả về HTTP 200 OK với thông tin voting sau khi user bỏ phiếu
    }
}

