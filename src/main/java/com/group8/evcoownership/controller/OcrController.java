package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.GroupWithVehicleResponse;
import com.group8.evcoownership.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OCR Services", description = "OCR và auto-fill thông tin xe từ hình ảnh")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/extract-vehicle-info")
    @Operation(summary = "Trích xuất thông tin xe từ hình ảnh", 
               description = "Sử dụng OCR để đọc và trích xuất thông tin brand, model từ hình ảnh cà vẹt xe")
    public CompletableFuture<GroupWithVehicleResponse.AutoFillInfo> extractVehicleInfo(
            @RequestParam("image") MultipartFile image) {
        
        long startTime = System.currentTimeMillis();
        return ocrService.processVehicleInfoFromImage(image, startTime);
    }

    @PostMapping("/extract-text")
    @Operation(summary = "Trích xuất text từ hình ảnh", 
               description = "Sử dụng OCR để đọc text từ hình ảnh")
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
                        extractedText != null ? ocrService.isVehicleRegistrationDocument(extractedText) : false);
                    
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
    @Operation(summary = "Kiểm tra hình ảnh có phải cà vẹt xe không", 
               description = "Kiểm tra xem hình ảnh có phải là giấy đăng ký xe dựa trên keywords")
    public CompletableFuture<Boolean> validateRegistrationDocument(@RequestParam("image") MultipartFile image) {
        return ocrService.extractTextFromImage(image)
                .thenApply(ocrService::isVehicleRegistrationDocument);
    }
}