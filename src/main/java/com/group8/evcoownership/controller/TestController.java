package com.group8.evcoownership.controller;

import com.group8.evcoownership.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final OcrService ocrService;

    @GetMapping("/ocr-config")
    public Map<String, Object> testOcrConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("googleVisionEnabled", ocrService.isGoogleVisionEnabled());
        result.put("ocrEngineInfo", ocrService.getOcrEngineInfo());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @PostMapping("/ocr-simple")
    public Map<String, Object> testOcrSimple(@RequestParam("image") MultipartFile image) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Testing OCR with image: {} (size: {} bytes)",
                    image.getOriginalFilename(), image.getSize());

            // Test OCR extraction
            String extractedText = ocrService.extractTextFromImage(image).get();

            result.put("success", true);
            result.put("imageName", image.getOriginalFilename());
            result.put("imageSize", image.getSize());
            result.put("textLength", extractedText != null ? extractedText.length() : 0);
            result.put("extractedText", extractedText);
            result.put("isRegistrationDocument",
                    extractedText != null ? ocrService.isVehicleRegistrationDocument(extractedText) : false);

            log.info("OCR test completed. Text length: {}",
                    extractedText != null ? extractedText.length() : 0);

        } catch (Exception e) {
            log.error("OCR test failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }

        return result;
    }
}
