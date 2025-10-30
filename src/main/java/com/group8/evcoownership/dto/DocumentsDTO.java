package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentsDTO {
    private DocumentTypeDTO citizenIdImages;      // CCCD
    private DocumentTypeDTO driverLicenseImages;  // GPLX
}
