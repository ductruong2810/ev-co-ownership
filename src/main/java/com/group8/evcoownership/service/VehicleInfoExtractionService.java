package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.VehicleInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class VehicleInfoExtractionService {

    // Danh sách các hãng xe phổ biến tại Việt Nam
    private static final Set<String> COMMON_BRANDS = new HashSet<>(Arrays.asList(
        "honda", "yamaha", "suzuki", "kawasaki", "ducati", "ktm", "bmw", "harley-davidson",
        "toyota", "hyundai", "kia", "mazda", "nissan", "mitsubishi", "subaru",
        "ford", "chevrolet", "volkswagen", "audi", "mercedes-benz", "lexus",
        "vinfast", "thaco", "isuzu",
        "piaggio", "vespa", "sym", "kymco", "benelli", "cfmoto", "qj motor"
    ));

    // Pattern để tìm thông tin xe từ text OCR
    private static final Pattern BRAND_MODEL_PATTERN = Pattern.compile(
        "(?i)(nhãn hiệu\\s*\\(?brand\\)?)[\\s:]*([a-zA-Z0-9\\s-]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern MODEL_PATTERN = Pattern.compile(
        "(?i)(số loại\\s*\\(?model\\s*code\\)?)[\\s:]*([a-zA-Z0-9\\s-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile(
        "(?i)(năm sản xuất|năm|year)[\\s:]*([0-9]{4})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LICENSE_PATTERN = Pattern.compile(
        "([0-9]{2}[A-Z][0-9]-[0-9]{3}\\.[0-9]{2})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CHASSIS_PATTERN = Pattern.compile(
        "([A-Z0-9]{10,17})",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract thông tin xe từ text OCR
     */
    public VehicleInfoDto extractVehicleInfo(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return new VehicleInfoDto("", "", "", "", "");
        }

        log.info("Extracting vehicle info from OCR text: {}", ocrText.substring(0, Math.min(200, ocrText.length())));

        String brand = extractBrand(ocrText);
        String model = extractModel(ocrText);
        String year = extractYear(ocrText);
        String licensePlate = extractLicensePlate(ocrText);
        String chassisNumber = extractChassisNumber(ocrText);

        VehicleInfoDto result = new VehicleInfoDto(brand, model, year, licensePlate, chassisNumber);
        log.info("Extracted vehicle info: {}", result);
        log.info("Full OCR text for debugging: {}", ocrText);
        
        return result;
    }

    /**
     * Extract brand từ text
     */
    private String extractBrand(String text) {
        // Tìm theo pattern nhãn hiệu
        Matcher matcher = BRAND_MODEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String brand = matcher.group(2).trim();
            return normalizeBrand(brand);
        }

        // Tìm brand trong danh sách common brands
        String lowerText = text.toLowerCase();
        for (String commonBrand : COMMON_BRANDS) {
            if (lowerText.contains(commonBrand.toLowerCase())) {
                return capitalizeFirstLetter(commonBrand);
            }
        }

        // Tìm các từ có thể là brand (từ đầu tiên của dòng có chứa "nhãn hiệu")
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("nhãn hiệu") || line.toLowerCase().contains("brand")) {
                String[] words = line.split("\\s+");
                for (int i = 0; i < words.length - 1; i++) {
                    if (words[i].toLowerCase().contains("nhãn") || words[i].toLowerCase().contains("brand")) {
                        if (i + 1 < words.length) {
                            String brand = words[i + 1].trim();
                            if (isValidBrand(brand)) {
                                return normalizeBrand(brand);
                            }
                        }
                    }
                }
            }
        }

        // Fallback: Tìm trực tiếp trong text nếu có YAMAHA, HONDA, SUZUKI, etc.
        String lowerTextForBrand = text.toLowerCase();
        for (String commonBrand : COMMON_BRANDS) {
            if (lowerTextForBrand.contains(commonBrand.toLowerCase())) {
                log.info("Found brand '{}' in text using fallback method", commonBrand);
                return capitalizeFirstLetter(commonBrand);
            }
        }
        
        // Additional fallback: Tìm trong dòng có chứa "nhãn hiệu" hoặc "brand"
        String[] brandLines = text.split("\n");
        for (String line : brandLines) {
            if (line.toLowerCase().contains("nhãn hiệu") || line.toLowerCase().contains("brand")) {
                log.info("Found brand line: {}", line);
                // Tìm từ sau dấu : hoặc sau "brand"
                String[] parts = line.split("[:]");
                if (parts.length > 1) {
                    String brand = parts[1].trim();
                    if (isValidBrand(brand)) {
                        log.info("Extracted brand from line: {}", brand);
                        return normalizeBrand(brand);
                    }
                }
            }
        }

        return "";
    }

    /**
     * Extract model từ text
     */
    private String extractModel(String text) {
        // Tìm theo pattern kiểu loại
        Matcher matcher = MODEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String model = matcher.group(2).trim();
            return normalizeModel(model);
        }

        // Tìm model trong các dòng có chứa "số loại", "kiểu loại" hoặc "model"
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("số loại") || line.toLowerCase().contains("kiểu loại") || line.toLowerCase().contains("model")) {
                String[] words = line.split("\\s+");
                for (int i = 0; i < words.length - 1; i++) {
                    if (words[i].toLowerCase().contains("số") || words[i].toLowerCase().contains("kiểu") || words[i].toLowerCase().contains("model")) {
                        if (i + 1 < words.length) {
                            String model = words[i + 1].trim();
                            if (isValidModel(model)) {
                                return normalizeModel(model);
                            }
                        }
                    }
                }
            }
        }

        // Fallback: Tìm các từ có thể là model (SIRIUS, WAVE, VISION, etc.)
        String[] commonModels = {"sirius", "wave", "vision", "lead", "airblade", "sh", "pcx", "click", "future", "winner", "exciter", "nouvo", "grande", "liberty", "vario", "fino", "mio", "jupiter", "fz", "mt", "r15", "r3", "r6", "r1"};
        String lowerTextForModel = text.toLowerCase();
        for (String model : commonModels) {
            if (lowerTextForModel.contains(model.toLowerCase())) {
                log.info("Found model '{}' in text using fallback method", model);
                return capitalizeFirstLetter(model);
            }
        }
        
        // Additional fallback: Tìm trong dòng có chứa "số loại" hoặc "model"
        String[] modelLines = text.split("\n");
        for (String line : modelLines) {
            if (line.toLowerCase().contains("số loại") || line.toLowerCase().contains("model")) {
                log.info("Found model line: {}", line);
                // Tìm từ sau dấu : hoặc sau "model"
                String[] parts = line.split("[:]");
                if (parts.length > 1) {
                    String model = parts[1].trim();
                    if (isValidModel(model)) {
                        log.info("Extracted model from line: {}", model);
                        return normalizeModel(model);
                    }
                }
            }
        }

        return "";
    }

    /**
     * Extract năm sản xuất
     */
    private String extractYear(String text) {
        Matcher matcher = YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(2);
        }

        // Tìm năm 4 chữ số trong text
        Pattern yearPattern = Pattern.compile("\\b(19|20)[0-9]{2}\\b");
        matcher = yearPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }

        return "";
    }

    /**
     * Extract biển số xe
     */
    private String extractLicensePlate(String text) {
        Matcher matcher = LICENSE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        
        // Fallback: Tìm trong text có chứa "biển số" hoặc "plate"
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase().contains("biển số") || line.toLowerCase().contains("plate")) {
                log.info("Found license plate line: {}", line);
                // Tìm pattern license plate trong dòng hiện tại và dòng tiếp theo
                for (int j = i; j < Math.min(i + 2, lines.length); j++) {
                    matcher = LICENSE_PATTERN.matcher(lines[j]);
                    if (matcher.find()) {
                        log.info("Extracted license plate from line {}: {}", j, matcher.group(1));
                        return matcher.group(1).toUpperCase();
                    }
                }
            }
        }
        
        return "";
    }

    /**
     * Extract số khung xe
     */
    private String extractChassisNumber(String text) {
        Matcher matcher = CHASSIS_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        
        // Fallback: Tìm trong text có chứa "số khung" hoặc "chassis"
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase().contains("số khung") || line.toLowerCase().contains("chassis")) {
                log.info("Found chassis line: {}", line);
                // Tìm pattern chassis trong dòng hiện tại và dòng tiếp theo
                for (int j = i; j < Math.min(i + 2, lines.length); j++) {
                    matcher = CHASSIS_PATTERN.matcher(lines[j]);
                    if (matcher.find()) {
                        log.info("Extracted chassis from line {}: {}", j, matcher.group(1));
                        return matcher.group(1).toUpperCase();
                    }
                }
            }
        }
        
        return "";
    }

    /**
     * Chuẩn hóa brand
     */
    private String normalizeBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) {
            return "";
        }

        brand = brand.trim().toLowerCase();
        
        // Mapping các tên brand phổ biến
        Map<String, String> brandMapping = new HashMap<>();
        brandMapping.put("honda", "Honda");
        brandMapping.put("yamaha", "Yamaha");
        brandMapping.put("suzuki", "Suzuki");
        brandMapping.put("kawasaki", "Kawasaki");
        brandMapping.put("toyota", "Toyota");
        brandMapping.put("hyundai", "Hyundai");
        brandMapping.put("kia", "KIA");
        brandMapping.put("mazda", "Mazda");
        brandMapping.put("nissan", "Nissan");
        brandMapping.put("mitsubishi", "Mitsubishi");
        brandMapping.put("ford", "Ford");
        brandMapping.put("chevrolet", "Chevrolet");
        brandMapping.put("vinfast", "VinFast");
        brandMapping.put("thaco", "THACO");

        return brandMapping.getOrDefault(brand, capitalizeFirstLetter(brand));
    }

    /**
     * Chuẩn hóa model
     */
    private String normalizeModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return "";
        }

        // Clean up text: remove newlines, extra characters, trim
        String cleaned = model.replaceAll("\\n", " ").replaceAll("\\s+", " ").trim();
        
        // Remove common suffixes that might be OCR artifacts
        cleaned = cleaned.replaceAll("\\s+[a-z]$", ""); // Remove single letter at end
        
        return cleaned.toUpperCase();
    }

    /**
     * Kiểm tra brand có hợp lệ không
     */
    private boolean isValidBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) {
            return false;
        }

        String lowerBrand = brand.toLowerCase().trim();
        return COMMON_BRANDS.contains(lowerBrand) || 
               (brand.length() >= 2 && brand.length() <= 20 && brand.matches("[a-zA-Z0-9\\s-]+"));
    }

    /**
     * Kiểm tra model có hợp lệ không
     */
    private boolean isValidModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return false;
        }

        String trimmedModel = model.trim();
        return trimmedModel.length() <= 50 && trimmedModel.matches("[a-zA-Z0-9\\s-]+");
    }

    /**
     * Viết hoa chữ cái đầu
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

}
