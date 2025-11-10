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
    private final JwtUtil jwtUtils;

    @PostMapping
    @Operation(summary = "Tạo phiếu bầu mới")
    public ResponseEntity<VotingResponseDTO> createVoting(
            @Valid @RequestBody CreateVotingWithGroupRequestDTO request,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));

        CreateVotingRequestDTO votingRequest = new CreateVotingRequestDTO();
        votingRequest.setTitle(request.getTitle());
        votingRequest.setDescription(request.getDescription());
        votingRequest.setVotingType(request.getVotingType());
        votingRequest.setOptions(request.getOptions());
        votingRequest.setDeadline(request.getDeadline());
        votingRequest.setEstimatedAmount(request.getEstimatedAmount());
        votingRequest.setRelatedExpenseId(request.getRelatedExpenseId());

        VotingResponseDTO response = votingService.createVoting(request.getGroupId(), votingRequest, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Nhận tất cả các phiếu bầu trong nhóm")
    public ResponseEntity<List<VotingResponseDTO>> getGroupVotings(
            @RequestParam Long groupId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        List<VotingResponseDTO> votings = votingService.getGroupVotings(groupId, userId);
        return ResponseEntity.ok(votings);
    }

    @GetMapping("/{votingId}")
    @Operation(summary = "Nhận chi tiết bỏ phiếu")
    public ResponseEntity<VotingResponseDTO> getVotingDetail(
            @PathVariable Long votingId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        VotingResponseDTO voting = votingService.getVotingDetail(votingId, userId);
        return ResponseEntity.ok(voting);
    }

    @PostMapping("/vote")
    @Operation(summary = "Bỏ phiếu")
    public ResponseEntity<VotingResponseDTO> vote(
            @Valid @RequestBody VoteWithIdsRequestDTO request,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));

        VoteRequestDTO voteRequest = new VoteRequestDTO();
        voteRequest.setSelectedOption(request.getSelectedOption());

        VotingResponseDTO response = votingService.vote(request.getVotingId(), voteRequest, userId);
        return ResponseEntity.ok(response);
    }
}
