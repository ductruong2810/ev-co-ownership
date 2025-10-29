package com.group8.evcoownership.service;

import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.group8.evcoownership.dto.GroupWithVehicleResponse;
import com.group8.evcoownership.dto.VehicleInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OcrService {

    @Value("${azure.computer-vision.endpoint:}")
    private String azureEndpoint;

    @Value("${azure.computer-vision.key:}")
    private String azureKey;

    @Value("${azure.computer-vision.enabled:true}")
    private boolean azureEnabled;

    private ImageAnalysisClient azureClient;
    private final VehicleInfoExtractionService vehicleInfoExtractionService;

    public OcrService(VehicleInfoExtractionService vehicleInfoExtractionService) {
        this.vehicleInfoExtractionService = vehicleInfoExtractionService;
    }

    /**
     * Khởi tạo Azure Computer Vision client
     */
    private void initializeAzureClient() {
        if (azureClient == null && azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty()) {
            try {
                azureClient = new ImageAnalysisClientBuilder()
                        .endpoint(azureEndpoint)
                        .credential(new AzureKeyCredential(azureKey))
                        .buildClient();
                log.info("Azure Computer Vision client initialized successfully");
            } catch (Exception e) {
                log.warn("Failed to initialize Azure Computer Vision client: {}", e.getMessage());
            }
        }
    }

    /**
     * Đọc text từ hình ảnh sử dụng Azure Computer Vision OCR
     */
    public CompletableFuture<String> extractTextFromImage(MultipartFile imageFile) {
        log.info("=== OCR EXTRACTION START ===");
        log.info("Image: {} (size: {} bytes)", imageFile.getOriginalFilename(), imageFile.getSize());
        log.info("Azure enabled: {}", azureEnabled);
        log.info("Azure endpoint: {}", azureEndpoint);
        log.info("Azure key configured: {}", !azureKey.isEmpty());

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty()) {
                    log.info("Using Azure Computer Vision for OCR extraction");
                    String azureResult = extractTextWithAzure(imageFile);
                    if (azureResult != null && !azureResult.trim().isEmpty()) {
                        log.info("Successfully extracted text using Azure Computer Vision. Text length: {}",
                                azureResult.length());
                        log.debug("Extracted text preview: {}",
                                azureResult.substring(0, Math.min(200, azureResult.length())));
                        return azureResult;
                    } else {
                        log.warn("Azure Computer Vision returned empty result");
                    }
                } else {
                    log.warn("Azure Computer Vision is disabled or not configured");
                    log.warn("azureEnabled: {}, endpoint empty: {}, key empty: {}",
                            azureEnabled, azureEndpoint.isEmpty(), azureKey.isEmpty());
                }

                log.warn("Failed to extract text from image using Azure Computer Vision");
                return "";

            } catch (Exception e) {
                log.error("Error extracting text from image: {}", e.getMessage(), e);
                return "";
            }
        }).orTimeout(60, TimeUnit.SECONDS);
    }

    /**
     * Sử dụng Azure Computer Vision để đọc text
     */
    private String extractTextWithAzure(MultipartFile imageFile) {
        try {
            log.info("Initializing Azure Computer Vision client...");
            initializeAzureClient();
            if (azureClient == null) {
                log.warn("Azure Computer Vision client not available");
                return null;
            }

            // Convert MultipartFile to BinaryData
            log.info("Converting image to BinaryData...");
            log.info("Original image size: {} bytes", imageFile.getSize());
            log.info("Original image name: {}", imageFile.getOriginalFilename());
            log.info("Original image content type: {}", imageFile.getContentType());

            BinaryData imageData;
            try {
                // Try different approaches to convert MultipartFile to BinaryData
                if (imageFile.getBytes() != null && imageFile.getBytes().length > 0) {
                    log.info("Using imageFile.getBytes() method");
                    imageData = BinaryData.fromBytes(imageFile.getBytes());
                } else {
                    log.info("Using imageFile.getInputStream() method");
                    imageData = BinaryData.fromStream(imageFile.getInputStream());
                }
                log.info("Image converted successfully, BinaryData size: {} bytes", imageData.getLength());
            } catch (Exception e) {
                log.error("Failed to convert image to BinaryData: {}", e.getMessage(), e);
                return null;
            }

            // Perform OCR
            log.info("Sending request to Azure Computer Vision API...");
            long startTime = System.currentTimeMillis();
            ImageAnalysisResult result = azureClient.analyze(
                    imageData,
                    Collections.singletonList(VisualFeatures.READ),
                    null
            );
            long endTime = System.currentTimeMillis();
            log.info("Azure Computer Vision API response received in {} ms", endTime - startTime);

            // Extract text from result
            StringBuilder extractedText = new StringBuilder();
            if (result.getRead() != null && result.getRead().getBlocks() != null) {
                result.getRead().getBlocks().forEach(block -> {
                    if (block.getLines() != null) {
                        block.getLines().forEach(line -> {
                            extractedText.append(line.getText()).append("\n");
                        });
                    }
                });
            }

            String resultText = extractedText.toString().trim();
            log.info("OCR extraction completed. Text length: {}", resultText.length());
            if (!resultText.isEmpty()) {
                log.info("Extracted text preview: {}",
                        resultText.substring(0, Math.min(300, resultText.length())));
            } else {
                log.warn("No text extracted from image");
            }
            return resultText.isEmpty() ? null : resultText;

        } catch (Exception e) {
            log.warn("Azure Computer Vision OCR failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Kiểm tra xem hình ảnh có phải là cà vẹt xe không dựa trên keywords
     */
    public boolean isVehicleRegistrationDocument(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return false;
        }

        String text = extractedText.toLowerCase();

        // Keywords thường có trong cà vẹt xe Việt Nam
        String[] vehicleKeywords = {
                "giấy đăng ký xe", "đăng ký xe", "cà vẹt", "biển số", "số khung", "số máy",
                "nhãn hiệu", "kiểu loại", "màu sắc", "năm sản xuất", "nơi sản xuất",
                "chủ xe", "địa chỉ", "ngày cấp", "cơ quan cấp", "phương tiện",
                "xe máy", "ô tô", "xe đạp điện", "xe điện", "motor", "car", "vehicle",
                "registration", "license plate", "chassis", "engine", "brand", "model"
        };

        int keywordCount = 0;
        for (String keyword : vehicleKeywords) {
            if (text.contains(keyword)) {
                keywordCount++;
            }
        }

        // Nếu có ít nhất 2 keywords thì có thể là cà vẹt xe
        return keywordCount >= 2;
    }

    /**
     * Lấy thông tin về OCR engine đang sử dụng
     */
    public String getOcrEngineInfo() {
        if (azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty()) {
            return "Azure Computer Vision OCR";
        }
        return "OCR Service Disabled";
    }

    /**
     * Kiểm tra trạng thái Azure Computer Vision
     */
    public boolean isAzureEnabled() {
        return azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty();
    }

    /**
     * Shared method to process OCR and extract vehicle information from image
     * This method consolidates the common logic used by both OcrController and OwnershipGroupService
     */
    public CompletableFuture<GroupWithVehicleResponse.AutoFillInfo> processVehicleInfoFromImage(
            MultipartFile image, long startTime) {

        return extractTextFromImage(image)
                .thenApply(extractedText -> {
                    try {
                        if (extractedText == null || extractedText.trim().isEmpty()) {
                            return new GroupWithVehicleResponse.AutoFillInfo(
                                    true, "", "", "", "", "", false, "No text extracted",
                                    System.currentTimeMillis() - startTime + "ms"
                            );
                        }

                        // Check if it's a vehicle registration document
                        boolean isRegistrationDocument = isVehicleRegistrationDocument(extractedText);

                        // Extract vehicle information
                        VehicleInfoDto vehicleInfo =
                                vehicleInfoExtractionService.extractVehicleInfo(extractedText);

                        long processingTime = System.currentTimeMillis() - startTime;

                        return new GroupWithVehicleResponse.AutoFillInfo(
                                true,
                                vehicleInfo.brand(),
                                vehicleInfo.model(),
                                vehicleInfo.year(),
                                vehicleInfo.licensePlate(),
                                vehicleInfo.chassisNumber(),
                                isRegistrationDocument,
                                "High", // OCR confidence level
                                processingTime + "ms"
                        );

                    } catch (Exception e) {
                        long processingTime = System.currentTimeMillis() - startTime;
                        return new GroupWithVehicleResponse.AutoFillInfo(
                                true, "", "", "", "", "", false, "Processing failed: " + e.getMessage(),
                                processingTime + "ms"
                        );
                    }
                });
    }
}