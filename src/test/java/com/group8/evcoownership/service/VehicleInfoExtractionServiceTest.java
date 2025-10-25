package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.VehicleInfoDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VehicleInfoExtractionServiceTest {

    private final VehicleInfoExtractionService service = new VehicleInfoExtractionService();

    @Test
    public void testExtractBrandAndModelFromRealOcrText() {
        // Test với text OCR thực tế từ logs
        String ocrText = """
            Tên chủ xe (Owner's full name):
            Số máy (Engine Nº):
            TRẦN THỊ THÀNH PHƯƠNG
            5C63645910
            Địa chỉ (Address):
            Số khung (Chassis Nº):
            12 Đg19 Kp5 Bình Chiếu TĐ
            C630CY645859
            HÀNH PI
            Nhãn hiệu (Brand): YAMAHA
            Số loại(Model code): SIRIUS
            Màu sơn (Color): Bac Đen
            Dung tích (Capacity): 110
            Đăng ký xe có giá trị đến ngày (date of expiry)/
            Thủ Đức, ngày (đặte) Đốcthắng 06
            nam20204℃
            Biển số đăng ký (Nº plate)
            EN CU
            (T)
            59X2-328.98
            Đăng ký lần đầu ngày:
            Date of first registration
            Thượng tá: Lê Thị Liên Hồng
            5/06/2012
            """;

        VehicleInfoDto result = service.extractVehicleInfo(ocrText);
        
        System.out.println("=== EXTRACTION RESULT ===");
        System.out.println("Brand: " + result.brand());
        System.out.println("Model: " + result.model());
        System.out.println("Year: " + result.year());
        System.out.println("License Plate: " + result.licensePlate());
        System.out.println("Chassis: " + result.chassisNumber());
        
        // Kiểm tra brand
        assertNotNull(result.brand());
        assertTrue(result.brand().toLowerCase().contains("yamaha"), 
            "Brand should contain YAMAHA, got: " + result.brand());
        
        // Kiểm tra model
        assertNotNull(result.model());
        assertTrue(result.model().toLowerCase().contains("sirius"), 
            "Model should contain SIRIUS, got: " + result.model());
        
        // Kiểm tra year
        assertNotNull(result.year());
        assertTrue(result.year().contains("2012"), 
            "Year should contain 2012, got: " + result.year());
        
        // Kiểm tra license plate
        assertNotNull(result.licensePlate());
        assertTrue(result.licensePlate().contains("59X2-328.98"), 
            "License plate should contain 59X2-328.98, got: " + result.licensePlate());
    }
}
