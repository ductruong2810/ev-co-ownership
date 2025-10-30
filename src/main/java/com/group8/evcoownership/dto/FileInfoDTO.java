package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileInfoDTO {
    private String blobName;
    private String url;
    private Long size;
    private String contentType;
    private java.time.OffsetDateTime lastModified;
    private boolean exists;
}