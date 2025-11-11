package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.GroupWithVehicleResponseDTO;
import com.group8.evcoownership.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OCR Services", description = "OCR và auto-fill thông tin xe từ hình ảnh")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/extract-vehicle-info")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(summary = "[CO_OWNER/STAFF/ADMIN] Trích xuất thông tin xe từ hình ảnh (Async)",
            description = """
                Sử dụng OCR để đọc và trích xuất thông tin brand, model từ hình ảnh cà vẹt xe.
                - Co-owner: dùng khi tạo hồ sơ xe mới hoặc hợp đồng.
                - Staff/Admin: có thể dùng để hỗ trợ xác minh hình ảnh xe.
                Trả về CompletableFuture (async).
                """)
    public CompletableFuture<GroupWithVehicleResponseDTO.AutoFillInfo> extractVehicleInfo(
            @RequestParam("image") MultipartFile image) {

        long startTime = System.currentTimeMillis();
        return ocrService.processVehicleInfoFromImage(image, startTime);
    }

    @PostMapping("/auto-fill-form")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(summary = "[CO_OWNER/STAFF/ADMIN] Tự động điền form từ hình ảnh cà vẹt xe",
            description = "Sử dụng OCR để trích xuất thông tin xe (biển số, số khung, hãng, model, năm) từ hình ảnh cà vẹt xe.")
    public GroupWithVehicleResponseDTO.AutoFillInfo autoFillForm(
            @RequestParam("image") MultipartFile image) throws Exception {

        long startTime = System.currentTimeMillis();
        log.info("Auto-fill form request received for image: {} (size: {} bytes)",
                image.getOriginalFilename(), image.getSize());

        GroupWithVehicleResponseDTO.AutoFillInfo result =
                ocrService.processVehicleInfoFromImage(image, startTime).get();

        log.info("Auto-fill form completed. Extracted license plate: {}, chassis: {}, brand: {}, model: {}",
                result.extractedLicensePlate(), result.extractedChassisNumber(),
                result.extractedBrand(), result.extractedModel());

        return result;
    }

    @PostMapping("/extract-text")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "[ADMIN/STAFF] Trích xuất text thô từ hình ảnh (debug)",
            description = """
                Sử dụng OCR để đọc text từ hình ảnh.
                Dành cho mục đích kiểm thử hoặc xác minh OCR trong môi trường nội bộ.
                """)
    public CompletableFuture<String> extractText(@RequestParam("image") MultipartFile image) {
        return ocrService.extractTextFromImage(image);
    }

    @PostMapping("/debug-extract-text")
    @Operation(summary = "Debug OCR text extraction",
            description = "Chỉ trích xuất text từ hình ảnh để debug")
    public CompletableFuture<Map<String, Object>> debugExtractText(
            @RequestParam("image") MultipartFile image) {

        long startTime = System.currentTimeMillis();
        log.info("Debug OCR request received for image: {} (size: {} bytes)",
                image.getOriginalFilename(), image.getSize());

        return ocrService.extractTextFromImage(image)
                .thenApply(extractedText -> {
                    long processingTime = System.currentTimeMillis() - startTime;

                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("processingTime", processingTime + "ms");
                    result.put("textLength", extractedText != null ? extractedText.length() : 0);
                    result.put("extractedText", extractedText);
                    result.put("isRegistrationDocument",
                            extractedText != null && ocrService.isVehicleRegistrationDocument(extractedText));

                    log.info("Debug OCR completed in {} ms, text length: {}",
                            processingTime, extractedText != null ? extractedText.length() : 0);

                    return result;
                })
                .exceptionally(throwable -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    log.error("Debug OCR failed after {} ms", processingTime, throwable);

                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("processingTime", processingTime + "ms");
                    result.put("error", throwable.getMessage());
                    return result;
                });
    }

    @GetMapping("/health")
    @Operation(summary = "OCR Service Health Check",
            description = "Kiểm tra trạng thái OCR service")
    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("azureEnabled", ocrService.isAzureEnabled());
        result.put("ocrEngineInfo", ocrService.getOcrEngineInfo());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @PostMapping("/validate-registration-document")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(summary = "[CO_OWNER/STAFF/ADMIN] Kiểm tra hình ảnh có phải cà vẹt xe không",
            description = """
                Kiểm tra xem hình ảnh có phải là giấy đăng ký xe dựa trên từ khóa nhận dạng.
                - Dùng để xác thực ảnh do người dùng upload trong quá trình đăng ký xe hoặc hợp đồng.
                """)
    public CompletableFuture<Boolean> validateRegistrationDocument(@RequestParam("image") MultipartFile image) {
        return ocrService.extractTextFromImage(image)
                .thenApply(ocrService::isVehicleRegistrationDocument);
    }
}