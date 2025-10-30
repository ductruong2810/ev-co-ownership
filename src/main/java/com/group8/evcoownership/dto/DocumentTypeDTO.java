package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeDTO {
    private DocumentDetailDTO front;  // Object hoặc null
    private DocumentDetailDTO back;   // Object hoặc null
}
