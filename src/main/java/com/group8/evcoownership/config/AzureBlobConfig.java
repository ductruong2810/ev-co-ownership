package com.group8.evcoownership.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobContainerAccessPolicies;
import com.azure.storage.blob.models.PublicAccessType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Slf4j
public class AzureBlobConfig {

    // inject tên của container từ file cấu hình
    // (application.properties hoặc application.yml)
    @Value("${azure.storage.container-name}")
    private String containerName;

    // ở đây cũng inject connection string từ file cấu hình
    // để mình kết noi với AzuresStrograge account
    @Value("${azure.storage.connection-string}")
    private String connectionString;

    //d9ăng ký một Spring Bean trả về BlobServiceClient --> để tuong tác với AzureStorage
    @Bean
    public BlobServiceClient blobServiceClient() {
        // +Kiểm tra connectionString null (chưa cấu hình) hoặc toàn dấu space (không có nội dung thực),
        // trường hợp này báo lỗi cấu hình.
        // +Sử dụng trim() để loại bỏ khoảng trắng đầu/cuối,
        // giúp phát hiện cấu hình sai dù nhập thừa dấu cách.
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalStateException("Azure connection string is not configured!");
        }

        // +generate BlobServiceClient từ thằng connection string,
        // cho phép truy cập các container và blob
        BlobServiceClient client = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        //Log để theo dõi xem kết nối duoc khong
        log.info("BlobServiceClient created successfully!");
        return client;
    }

    // Định nghĩa Bean BlobContainerClient để thao tác trực tiếp với một container blob cụ thể
    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        if (containerName == null || containerName.trim().isEmpty()) {
            throw new IllegalStateException("Azure container name is not configured!");
        }

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        // CREATE CONTAINER IF NOT EXISTS
        if (!containerClient.exists()) {
            log.info("Container does NOT exist, creating: {}", containerName);
            containerClient.create();
            log.info("Container created successfully: {}", containerName);
        } else {
            log.info("Container already exists: {}", containerName);
        }

        // SET PUBLIC ACCESS TO BLOB (IMPORTANT!)
        try {
            BlobContainerAccessPolicies accessPolicies = containerClient.getAccessPolicy();
            PublicAccessType currentAccess = accessPolicies.getBlobAccessType();

            if (currentAccess != PublicAccessType.BLOB) {
                log.info("Setting public access to BLOB for container: {}", containerName);
                containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
                log.info("Public access set to BLOB successfully");
            } else {
                log.info("Container already has BLOB public access");
            }
        } catch (Exception e) {
            log.warn("Could not set public access (may need Azure portal config): {}", e.getMessage());
        }

        return containerClient;
    }
}
