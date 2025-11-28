package com.group8.evcoownership.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.group8.evcoownership.dto.GroupWithVehicleResponseDTO;
import com.group8.evcoownership.dto.VehicleInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OcrService {

    @Value("${ocr.provider:google}")
    private String ocrProvider;   // OCR provider: google

    @Value("${google.vision.api-key:}")
    private String googleVisionApiKey;   // Google Vision API key

    @Value("${google.vision.endpoint:https://vision.googleapis.com/v1/images:annotate}")
    private String googleVisionEndpoint;   // Google Vision endpoint

    private final VehicleInfoExtractionService vehicleInfoExtractionService;

    public OcrService(VehicleInfoExtractionService vehicleInfoExtractionService) {
        this.vehicleInfoExtractionService = vehicleInfoExtractionService;
    }

    // ========= OCR ảnh -> text (dùng cho CCCD / GPLX và cà vẹt xe) =========
    // OCR ảnh thành text bất đồng bộ, trả về CompletableFuture<String>
    public CompletableFuture<String> extractTextFromImage(MultipartFile imageFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Sử dụng Google Vision OCR
                if ("google".equalsIgnoreCase(ocrProvider)) {
                    String googleResult = extractTextWithGoogleVision(imageFile);
                    if (googleResult != null && !googleResult.trim().isEmpty()) {
                        return googleResult;
                    }
                } else {
                    log.warn("OCR provider '{}' is not supported. Only 'google' is supported.", ocrProvider);
                }
                // Trả về chuỗi rỗng nếu ocr ko thành công
                return "";
            } catch (Exception e) {
                // Bắt lỗi phát sinh, log lỗi và trả chuỗi rỗng để tránh chết luồng
                log.error("Error extracting text from image: {}", e.getMessage(), e);
                return "";
            }
        }).orTimeout(60, TimeUnit.SECONDS); // Mình giới hạn timeout 60s cho thằng ocr
    }

    // ========= Hàm gọi Google Vision OCR =========
    private String extractTextWithGoogleVision(MultipartFile imageFile) {
        try {
            ByteString imgBytes = ByteString.copyFrom(imageFile.getBytes());

            List<AnnotateImageRequest> requests = new ArrayList<>();
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request =
                    AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            requests.add(request);

            long startTime = System.currentTimeMillis();
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                List<AnnotateImageResponse> responses = response.getResponsesList();

                StringBuilder sb = new StringBuilder();
                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        log.error("Google Vision OCR error: {}", res.getError().getMessage());
                        throw new RuntimeException("Google Vision OCR failed: " + res.getError().getMessage());
                    }
                    for (EntityAnnotation annotation : res.getTextAnnotationsList()) {
                        sb.append(annotation.getDescription()).append("\n");
                    }
                }
                long processing = System.currentTimeMillis() - startTime;
                log.info("Google Vision OCR call took {} ms", processing);

                String resultText = sb.toString().trim();
                if (resultText.isEmpty()) {
                    log.warn("No text extracted from image");
                    return null;
                }
                return resultText;
            }
        } catch (Exception e) {
            log.error("Google Vision OCR failed: {}", e.getMessage(), e);
            return null;
        }
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
        if ("google".equalsIgnoreCase(ocrProvider)) {
            return "Google Vision OCR";
        }
        return "OCR Service Disabled";
    }

    /**
     * Kiểm tra trạng thái Google Vision OCR
     */
    public boolean isGoogleVisionEnabled() {
        return "google".equalsIgnoreCase(ocrProvider) &&
                googleVisionApiKey != null && !googleVisionApiKey.isEmpty();
    }

    /**
     * Shared method to process OCR and extract vehicle information from image
     * This method consolidates the common logic used by both OcrController and OwnershipGroupService
     */
    public CompletableFuture<GroupWithVehicleResponseDTO.AutoFillInfo> processVehicleInfoFromImage(
            MultipartFile image, long startTime) {

        return extractTextFromImage(image)
                .thenApply(extractedText -> {
                    try {
                        if (extractedText == null || extractedText.trim().isEmpty()) {
                            return new GroupWithVehicleResponseDTO.AutoFillInfo(
                                    true, "", "", "", "", "", false, "No text extracted",
                                    System.currentTimeMillis() - startTime + "ms"
                            );
                        }

                        // Check if it's a vehicle registration document
                        boolean isRegistrationDocument = isVehicleRegistrationDocument(extractedText);

                        // Extract vehicle information
                        VehicleInfoDTO vehicleInfo =
                                vehicleInfoExtractionService.extractVehicleInfo(extractedText);

                        long processingTime = System.currentTimeMillis() - startTime;

                        return new GroupWithVehicleResponseDTO.AutoFillInfo(
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
                        return new GroupWithVehicleResponseDTO.AutoFillInfo(
                                true, "", "", "", "", "", false, "Processing failed: " + e.getMessage(),
                                processingTime + "ms"
                        );
                    }
                });
    }
}