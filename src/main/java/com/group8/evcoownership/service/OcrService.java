package com.group8.evcoownership.service;

import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.group8.evcoownership.dto.GroupWithVehicleResponseDTO;
import com.group8.evcoownership.dto.VehicleInfoDTO;
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
    private String azureEndpoint;   // Endpoint dịch vụ Azure Computer Vision

    @Value("${azure.computer-vision.key:}")
    private String azureKey;        // API key dùng để gọi Azure Computer Vision

    @Value("${azure.computer-vision.enabled:true}")
    private boolean azureEnabled;   // Cờ bật/tắt OCR Azure theo cấu hình

    private ImageAnalysisClient azureClient; // Client gọi Azure OCR (được lazy-init)
    private final VehicleInfoExtractionService vehicleInfoExtractionService;

    public OcrService(VehicleInfoExtractionService vehicleInfoExtractionService) {
        this.vehicleInfoExtractionService = vehicleInfoExtractionService;
    }

    // ========= Khởi tạo client azure computer vision =========
    // Dùng endpoint + key cấu hình để tạo client gọi API OCR Azure
    // Chỉ khởi tạo 1 lần, dùng lại cho các lần gọi sau
    private void initializeAzureClient() {
        // Kiểm tra nếu client chưa tạo, OCR được bật, endpoint và key có cấu hình hợp lệ
        if (azureClient == null && azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty()) {
            try {
                // Dùng builder để tạo client với endpoint và key
                azureClient = new ImageAnalysisClientBuilder()
                        .endpoint(azureEndpoint)
                        .credential(new AzureKeyCredential(azureKey))
                        .buildClient();
                log.info("successfully");
            } catch (Exception e) {
                // Nếu lỗi trong quá trình tạo client, log cảnh báo nhưng không crash app
                log.warn("Failed to initialize ACV client: {}", e.getMessage());
            }
        }
    }

    // ========= OCR ảnh -> text (dùng cho CCCD / GPLX và cà vẹt xe) =========
    // OCR ảnh thành text bất đồng bộ, trả về CompletableFuture<String>
    public CompletableFuture<String> extractTextFromImage(MultipartFile imageFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Chỉ gọi OCR nếu chức năng đang bật và đủ cấu hình key, endpoint
                if (azureEnabled && !azureEndpoint.isEmpty() && !azureKey.isEmpty()) {
                    // Gọi hàm dùng Azure OCR để trích xuất text
                    String azureResult = extractTextWithAzure(imageFile);

                    // Nếu có kết quả text hợp lệ thì trả về
                    if (azureResult != null && !azureResult.trim().isEmpty()) {
                        return azureResult;
                    }
                } else {
                    // Nếu bị tắt hoặc không đủ cấu hình, log cảnh báo
                    log.warn("ACV is disabled or not configured");
                }
                // Trả về chuỗi rỗng nếu ocr ko thành công
                return "";
            } catch (Exception e) {
                // Bắt lỗi phát sinh, log lỗi và trả chuỗi rỗng để tránh chết luồng
                log.error("Error extracting text from image: {}", e.getMessage());
                return "";
            }
        }).orTimeout(60, TimeUnit.SECONDS); // Mình giới hạn timeout 60s cho thằng ocr
    }

    // ========= Hàm gọi azure computer vision =========
    // Chỉ được gọi từ extractTextFromImage, không dùng trực tiếp ngoài service
    // 1. Khởi tạo client nếu chưa có
    // 2. Convert MultipartFile -> BinaryData (bytes hoặc stream)
    // 3. Gọi azureClient.analyze(...) với VisualFeatures.READ
    // 4. Duyệt qua blocks/lines trong kết quả, ghép lại thành text
    private String extractTextWithAzure(MultipartFile imageFile) {
        try {
            // Khoi tạo client nếu chưa có
            initializeAzureClient();
            if (azureClient == null) {
                // Nếu không khởi tạo được client (thiếu config)
                // trả null để báo lỗi
                return null;
            }

            BinaryData imageData;
            try {
                // Chuyển MultipartFile sang BinaryData ( vì thằng Azure yêu cầu)
                // Nếu ảnh có bytes dùng luôn, không thì lấy InputStream
                if (imageFile.getBytes().length > 0) {
                    imageData = BinaryData.fromBytes(imageFile.getBytes());
                } else {
                    imageData = BinaryData.fromStream(imageFile.getInputStream());
                }
            } catch (Exception e) {
                // Log lỗi chuyển đổi file và trả về null
                log.error("Failed to convert image to BinaryData: {}", e.getMessage());
                return null;
            }

            // Gọi API ACV để phân tích ảnh
            // chỉ lấy VisualFeatures.READ (OCR)
            long startTime = System.currentTimeMillis();
            ImageAnalysisResult result = azureClient.analyze(
                    imageData,
                    Collections.singletonList(VisualFeatures.READ),
                    null
            );
            long processing = System.currentTimeMillis() - startTime;
            log.info("Azure OCR call took {} ms", processing);

            // Ghép các dòng text từ kết quả OCR thành 1 chuỗi hoàn chỉnh
            StringBuilder extractedText = new StringBuilder();
            if (result.getRead() != null && result.getRead().getBlocks() != null) {
                result.getRead().getBlocks().forEach(block -> {
                    if (block.getLines() != null) {
                        block.getLines().forEach(line -> extractedText.append(line.getText()).append("\n"));
                    }
                });
            }

            // Lấy chuỗi text cuối cùng, loại bỏ khoảng trắng thừa
            String resultText = extractedText.toString().trim();
            if (resultText.isEmpty()) {
                log.warn("No text extracted from image");
                return null;
            }
            return resultText;

        } catch (Exception e) {
            // Bất kỳ lỗi nào xảy ra khi gọi Azure OCR, log và trả null
            log.warn("ACV OCR failed: {}", e.getMessage());
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