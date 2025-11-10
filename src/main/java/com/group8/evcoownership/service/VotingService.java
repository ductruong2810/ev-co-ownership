package com.group8.evcoownership.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.exception.BadRequestException;
import com.group8.evcoownership.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VotingService {
    private final VotingRepository votingRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public VotingResponseDTO createVoting(Long groupId, CreateVotingRequestDTO request, Long creatorId) {
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(groupId, creatorId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        try {
            // Chuyển List<VotingOption> thành Map<String, String> để lưu vào DB
            Map<String, String> optionsMap = request.getOptions().stream()
                    .collect(Collectors.toMap(
                            CreateVotingRequestDTO.VotingOption::getKey,
                            CreateVotingRequestDTO.VotingOption::getLabel
                    ));

            String optionsJson = objectMapper.writeValueAsString(optionsMap);

            Voting voting = Voting.builder()
                    .groupId(groupId)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .votingType(request.getVotingType())
                    .options(optionsJson)
                    .deadline(request.getDeadline())
                    .status("ACTIVE")
                    .createdBy(creatorId)
                    .relatedExpenseId(request.getRelatedExpenseId())
                    .estimatedAmount(request.getEstimatedAmount())
                    .build();

            voting = votingRepository.save(voting);
            return mapToResponse(voting, creatorId);
        } catch (Exception e) {
            throw new BadRequestException("Failed to create voting: " + e.getMessage());
        }
    }


    @Transactional
    public VotingResponseDTO vote(Long votingId, VoteRequestDTO request, Long userId) {
        Voting voting = votingRepository.findById(votingId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting not found"));

        if (!"ACTIVE".equals(voting.getStatus())) {
            throw new BadRequestException("Voting is not active");
        }

        if (voting.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Voting deadline has passed");
        }

        // SỬA Ở ĐÂY
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(voting.getGroupId(), userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        if (voteRecordRepository.existsByVotingIdAndUserId(votingId, userId)) {
            throw new BadRequestException("You have already voted");
        }

        VoteRecord voteRecord = VoteRecord.builder()
                .votingId(votingId)
                .userId(userId)
                .selectedOption(request.getSelectedOption())
                .build();

        voteRecordRepository.save(voteRecord);
        updateVotingResults(voting);

        return mapToResponse(voting, userId);
    }

    private void updateVotingResults(Voting voting) {
        List<VoteRecord> votes = voteRecordRepository.findByVotingId(voting.getVotingId());
        Map<String, Long> results = votes.stream()
                .collect(Collectors.groupingBy(VoteRecord::getSelectedOption, Collectors.counting()));

        try {
            voting.setResults(objectMapper.writeValueAsString(results));
            votingRepository.save(voting);
        } catch (Exception e) {
            throw new BadRequestException("Failed to update results");
        }
    }

    /**
     * Lấy danh sách tất cả votings trong group
     */
    public List<VotingResponseDTO> getGroupVotings(Long groupId, Long userId) {
        // Kiểm tra user có phải member không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        List<Voting> votings = votingRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        return votings.stream()
                .map(v -> mapToResponse(v, userId))
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết một voting
     */
    public VotingResponseDTO getVotingDetail(Long votingId, Long userId) {
        Voting voting = votingRepository.findById(votingId)
                .orElseThrow(() -> new ResourceNotFoundException("Voting not found"));

        // Kiểm tra user có phải member của group không
        if (!ownershipShareRepository.existsByGroup_GroupIdAndUser_UserId(voting.getGroupId(), userId)) {
            throw new BadRequestException("You are not a member of this group");
        }

        return mapToResponse(voting, userId);
    }

    private VotingResponseDTO mapToResponse(Voting voting, Long userId) {
        try {
            Map<String, Object> options = objectMapper.readValue(voting.getOptions(), new TypeReference<>() {});
            Map<String, Object> results = voting.getResults() != null ?
                    objectMapper.readValue(voting.getResults(), new TypeReference<>() {}) : new HashMap<>();

            Optional<VoteRecord> userVote = voteRecordRepository.findByVotingIdAndUserId(voting.getVotingId(), userId);

            // Tính tổng số votes
            int totalVotes = (int)voteRecordRepository.countByVotingId(voting.getVotingId());

            // Tính tổng số members trong group
            int totalMembers = (int)ownershipShareRepository.countByGroup_GroupId(voting.getGroupId());

            // Tính voting progress
            String votingProgress = totalMembers > 0
                    ? String.format("%.1f%%", (totalVotes * 100.0 / totalMembers))
                    : "0%";

            // Lấy tên người tạo từ User entity
            String createdByName = userRepository.findById(voting.getCreatedBy())
                    .map(User::getFullName) // Sử dụng getFullName() thay vì getUsername()
                    .orElse(null);

            // Tính thời gian còn lại
            String timeRemaining = calculateTimeRemaining(voting.getDeadline());

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
                    .options(options)
                    .results(results)
                    .createdBy(voting.getCreatedBy())
                    .createdByName(createdByName)
                    .createdAt(voting.getCreatedAt())
                    .hasVoted(userVote.isPresent())
                    .userVote(userVote.map(VoteRecord::getSelectedOption).orElse(null))
                    .totalVotes(totalVotes)
                    .totalMembers(totalMembers)
                    .votingProgress(votingProgress)
                    .timeRemaining(timeRemaining)
                    .build();
        } catch (Exception e) {
            throw new BadRequestException("Failed to map voting response: " + e.getMessage());
        }
    }

    // Helper method tính thời gian còn lại
    private String calculateTimeRemaining(LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();
        if (deadline.isBefore(now)) {
            return "Expired";
        }

        long hours = java.time.Duration.between(now, deadline).toHours();
        if (hours < 24) {
            return hours + " hours";
        }

        long days = java.time.Duration.between(now, deadline).toDays();
        return days + " days";
    }

}
