package com.group8.evcoownership.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group8.evcoownership.dto.CreateVotingRequestDTO;
import com.group8.evcoownership.dto.VoteRequestDTO;
import com.group8.evcoownership.dto.VotingResponseDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.VoteRecord;
import com.group8.evcoownership.entity.Voting;
import com.group8.evcoownership.exception.BadRequestException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VoteRecordRepository;
import com.group8.evcoownership.repository.VotingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VotingService {
    private final VotingRepository votingRepository;
    // Làm việc với bảng Voting trong database

    private final VoteRecordRepository voteRecordRepository;
    // Làm việc với bảng VoteRecord
    // lưu từng lượt vote của user

    private final OwnershipShareRepository ownershipShareRepository;
    // Dùng để kiểm tra user có phải member của group hay không
    // và đếm số member trong group

    private final UserRepository userRepository;
    // Dùng để lấy thông tin user
    // ví dụ tên người tạo voting

    private final ObjectMapper objectMapper;
    // Dùng để convert giữa Object và JSON (Map -> JSON string, JSON string -> Map)


    // ========= Tạo voting mới =========
    @Transactional
    public VotingResponseDTO createVoting(Long groupId, CreateVotingRequestDTO request, Long creatorId) {
        // Kiểm tra người tạo có phải là thành viên của group không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(groupId, creatorId)) {
            // Nếu không phải member thì ném lỗi BadRequest
            throw new BadRequestException("You are not a member of this group");
        }

        try {
            // Chuyển List<VotingOption> thành Map<key, label> để lưu đơn giản trong DB dưới dạng JSON
            Map<String, String> optionsMap = request.getOptions().stream()
                    .collect(Collectors.toMap(
                            CreateVotingRequestDTO.VotingOption::getKey,   // key là mã option
                            CreateVotingRequestDTO.VotingOption::getLabel  // label là nội dung hiển thị
                    ));

            // Convert Map options thành JSON string
            String optionsJson = objectMapper.writeValueAsString(optionsMap);

            // Dùng builder để tạo entity Voting mới
            Voting voting = Voting.builder()
                    .groupId(groupId)                                  // group mà voting này thuộc về
                    .title(request.getTitle())                         // tiêu đề voting
                    .description(request.getDescription())             // mô tả chi tiết
                    .votingType(request.getVotingType())               // loại voting (single, multiple)
                    .options(optionsJson)                              // lưu options dưới dạng JSON string
                    .deadline(request.getDeadline())                   // hạn chót bỏ phiếu
                    .status("ACTIVE")                                  // trạng thái mặc định khi tạo mới là ACTIVE
                    .createdBy(creatorId)                              // id người tạo voting
                    .relatedExpenseId(request.getRelatedExpenseId())   // id chi phí liên quan nếu có
                    .estimatedAmount(request.getEstimatedAmount())     // số tiền ước tính nếu có
                    .build();

            // Lưu voting vào DB
            voting = votingRepository.save(voting);

            // Map entity sang DTO để trả về cho client
            return mapToResponse(voting, creatorId);
        } catch (Exception e) {
            // Bắt mọi lỗi phát sinh trong quá trình xử lý và trả về BadRequest với message chi tiết
            throw new BadRequestException("Failed to create voting: " + e.getMessage());
        }
    }


    // ========= Xử lý bỏ phiếu =========
    @Transactional
    public VotingResponseDTO vote(Long votingId, VoteRequestDTO request, Long userId) {
        // Tìm voting theo id, nếu không có thì ném lỗi not found
        Voting voting = votingRepository.findById(votingId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting not found"));

        // Kiểm tra trạng thái voting phải là ACTIVE mới được vote
        if (!"ACTIVE".equals(voting.getStatus())) {
            throw new BadRequestException("Voting is not active");
        }

        // Kiểm tra deadline, nếu đã quá hạn thì không cho vote nữa
        if (voting.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Voting deadline has passed");
        }

        // Kiểm tra user có phải là thành viên của group của voting này không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(voting.getGroupId(), userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        // Kiểm tra user đã vote ở voting này trước đó chưa
        if (voteRecordRepository.existsByVotingIdAndUserId(votingId, userId)) {
            throw new BadRequestException("You have already voted");
        }

        // Tạo bản ghi vote mới cho user
        VoteRecord voteRecord = VoteRecord.builder()
                .votingId(votingId)                         // gắn với voting nào
                .userId(userId)                             // ai là người vote
                .selectedOption(request.getSelectedOption())// option mà user chọn
                .build();

        // Lưu lượt vote vào DB
        voteRecordRepository.save(voteRecord);

        // Cập nhật kết quả tổng hợp của voting (số vote cho từng option)
        updateVotingResults(voting);

        // Trả về thông tin voting sau khi user vote, có kèm trạng thái user đã vote, kết quả
        return mapToResponse(voting, userId);
    }


    // ========= Cập nhật kết quả tổng hợp =========
    private void updateVotingResults(Voting voting) {
        // Lấy toàn bộ các bản ghi vote của voting này
        List<VoteRecord> votes = voteRecordRepository.findByVotingId(voting.getVotingId());

        // Gom nhóm theo selectedOption và đếm số lượng mỗi option
        Map<String, Long> results = votes.stream()
                .collect(Collectors.groupingBy(
                        VoteRecord::getSelectedOption,      // nhóm theo option
                        Collectors.counting()               // đếm số vote cho mỗi option
                ));

        try {
            // Convert map kết quả thành JSON string rồi lưu vào field results của Voting
            voting.setResults(objectMapper.writeValueAsString(results));

            // Lưu lại Voting với kết quả mới
            votingRepository.save(voting);
        } catch (Exception e) {
            // Nếu convert lỗi thì ném BadRequest
            throw new BadRequestException("Failed to update results");
        }
    }


    // ========= Lấy danh sách voting trong group =========
    public List<VotingResponseDTO> getGroupVotings(Long groupId, Long userId) {
        // Kiểm tra user có phải member của group không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        // Lấy danh sách voting của group, sắp xếp theo thời gian tạo mới nhất trước
        List<Voting> votings = votingRepository.findByGroupIdOrderByCreatedAtDesc(groupId);

        // Map từng Voting entity sang VotingResponseDTO
        return votings.stream()
                .map(v -> mapToResponse(v, userId))
                .collect(Collectors.toList());
    }


    // ========= Lấy detail một voting =========
    public VotingResponseDTO getVotingDetail(Long votingId, Long userId) {
        // Tìm voting theo id, nếu không thấy thì ném lỗi
        Voting voting = votingRepository.findById(votingId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting not found"));

        // Kiểm tra user có phải member của group chứa voting này không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(voting.getGroupId(), userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        // Trả về DTO chi tiết voting cho client
        return mapToResponse(voting, userId);
    }


    // ========= Map entity → Reponse DTO=========
    private VotingResponseDTO mapToResponse(Voting voting, Long userId) {
        try {
            // Parse JSON options trong Voting thành Map để trả về cho client
            Map<String, Object> options = objectMapper.readValue(
                    voting.getOptions(),
                    new TypeReference<>() {
                    }
            );

            // Nếu có kết quả thì parse JSON results thành Map, nếu chưa có thì dùng HashMap rỗng
            Map<String, Object> results = voting.getResults() != null
                    ? objectMapper.readValue(voting.getResults(), new TypeReference<>() {
            })
                    : new HashMap<>();

            // Tìm bản ghi vote của user trong voting này (nếu đã vote)
            Optional<VoteRecord> userVote = voteRecordRepository
                    .findByVotingIdAndUserId(voting.getVotingId(), userId);

            // Đếm tổng số lượt vote của voting
            int totalVotes = (int) voteRecordRepository.countByVotingId(voting.getVotingId());

            // Đếm tổng số thành viên trong group của voting
            int totalMembers = (int) ownershipShareRepository.countByGroup_GroupId(voting.getGroupId());

            // Tính phần trăm số người đã vote trên tổng số thành viên
            String votingProgress = totalMembers > 0
                    ? String.format("%.1f%%", (totalVotes * 100.0 / totalMembers))
                    : "0%";

            // Lấy tên người tạo từ User entity, dùng fullName
            String createdByName = userRepository.findById(voting.getCreatedBy())
                    .map(User::getFullName)
                    .orElse(null);

            // Tính thời gian còn lại tới deadline ở dạng chuỗi
            String timeRemaining = calculateTimeRemaining(voting.getDeadline());

            // Build VotingResponseDTO trả về cho client
            return VotingResponseDTO.builder()
                    .votingId(voting.getVotingId())
                    .groupId(voting.getGroupId())
                    .title(voting.getTitle())
                    .description(voting.getDescription())
                    .votingType(voting.getVotingType())
                    .status(voting.getStatus())
                    .deadline(voting.getDeadline())
                    .estimatedAmount(voting.getEstimatedAmount())
                    .relatedExpenseId(voting.getRelatedExpenseId())
                    .options(options)                                         // map options đã parse từ JSON
                    .results(results)                                         // map kết quả đã parse từ JSON
                    .createdBy(voting.getCreatedBy())
                    .createdByName(createdByName)
                    .createdAt(voting.getCreatedAt())
                    .hasVoted(userVote.isPresent())                           // user đã vote hay chưa
                    .userVote(userVote.map(VoteRecord::getSelectedOption).orElse(null)) // option user đã chọn
                    .totalVotes(totalVotes)                                   // tổng số lượt vote
                    .totalMembers(totalMembers)                               // tổng số member trong group
                    .votingProgress(votingProgress)                           // phần trăm tham gia vote
                    .timeRemaining(timeRemaining)                             // thời gian còn lại tới deadline
                    .build();
        } catch (Exception e) {
            // Nếu có lỗi khi map dữ liệu thì ném BadRequest với message chi tiết
            throw new BadRequestException("Failed to map voting response: " + e.getMessage());
        }
    }


    // ========= Tính thời gian còn lại =========
    // Helper method tính thời gian còn lại tới deadline
    private String calculateTimeRemaining(LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();

        // Nếu deadline đã trước thời điểm hiện tại thì trả về Expired
        if (deadline.isBefore(now)) {
            return "Expired";
        }

        // Tính tổng số giờ còn lại
        long hours = java.time.Duration.between(now, deadline).toHours();
        if (hours < 24) {
            // Nếu dưới 24 giờ thì hiển thị theo giờ
            return hours + " hours";
        }

        // Nếu từ 1 ngày trở lên thì hiển thị theo ngày
        long days = java.time.Duration.between(now, deadline).toDays();
        return days + " days";
    }

}
