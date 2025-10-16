package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ExpenseCreateRequest;
import com.group8.evcoownership.dto.ExpenseResponse;
import com.group8.evcoownership.dto.ExpenseUpdateRequest;
import com.group8.evcoownership.service.ExpenseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(
            @PathVariable Long id,
            @RequestBody ExpenseUpdateRequest req
    ) {
        return ResponseEntity.ok(expenseService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ví dụ:
     * /api/expenses?fundId=1&sourceType=MAINTENANCE&from=2025-10-01T00:00:00&to=2025-10-31T23:59:59&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<ExpenseResponse>> list(
            @RequestParam(required = false) Long fundId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @ParameterObject
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(expenseService.list(fundId, sourceType, from, to, pageable));
    }

    /* Xử lý lỗi tối giản */
    @ExceptionHandler({EntityNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<String> handleNotFound(RuntimeException ex) {
        HttpStatus status = (ex instanceof EntityNotFoundException) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ex.getMessage());
    }
}

