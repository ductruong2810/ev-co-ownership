package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.PreUseCheck;
import com.group8.evcoownership.service.PreUseCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/pre-use-checks")
@RequiredArgsConstructor
public class PreUseCheckController {

    private final PreUseCheckService preUseCheckService;

    /**
     * User tạo pre-use check
     * Example:
     * POST /api/pre-use-checks
     */
    @PostMapping
    public ResponseEntity<PreUseCheck> createPreUseCheck(
            @RequestParam Long bookingId,
            @RequestParam Boolean exteriorDamage,
            @RequestParam Boolean interiorClean,
            @RequestParam Boolean warningLights,
            @RequestParam Boolean tireCondition,
            @RequestParam(required = false) String userNotes) {

        PreUseCheck check = preUseCheckService.createPreUseCheck(
                bookingId, exteriorDamage, interiorClean, warningLights, tireCondition, userNotes);

        return ResponseEntity.ok(check);
    }

    /**
     * Lấy pre-use check của booking
     * Example:
     * GET /api/pre-use-checks/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<Optional<PreUseCheck>> getPreUseCheck(@PathVariable Long bookingId) {
        Optional<PreUseCheck> check = preUseCheckService.getPreUseCheck(bookingId);
        return ResponseEntity.ok(check);
    }

    /**
     * Kiểm tra user đã làm pre-use check chưa
     * Example:
     * GET /api/pre-use-checks/has-check/{bookingId}
     */
    @GetMapping("/has-check/{bookingId}")
    public ResponseEntity<Boolean> hasPreUseCheck(@PathVariable Long bookingId) {
        Boolean hasCheck = preUseCheckService.hasPreUseCheck(bookingId);
        return ResponseEntity.ok(hasCheck);
    }
}
