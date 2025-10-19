package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.TemplateEditRequest;
import com.group8.evcoownership.dto.TemplateEditResponse;
import com.group8.evcoownership.dto.TemplateSectionResponse;
import com.group8.evcoownership.enums.TemplateSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TemplateEditorService {

    private final TemplateService templateService;

    // Patterns để extract các sections từ HTML
    private static final Map<TemplateSection, Pattern> SECTION_PATTERNS = createSectionPatterns();

    private static Map<TemplateSection, Pattern> createSectionPatterns() {
        Map<TemplateSection, Pattern> patterns = new HashMap<>();
        patterns.put(TemplateSection.HEADER, Pattern.compile("(?s)<div class=\"header\">(.*?)</div>"));
        patterns.put(TemplateSection.VEHICLE_INFO, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>1\\. Thông tin xe</h2>(.*?)</div>"));
        patterns.put(TemplateSection.FINANCE_TERMS, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>2\\. Góp vốn & Quỹ vận hành</h2>(.*?)</div>"));
        patterns.put(TemplateSection.USAGE_RULES, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>3\\. Quy tắc sử dụng</h2>(.*?)</div>"));
        patterns.put(TemplateSection.MAINTENANCE, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>4\\. Bảo dưỡng & Sửa chữa</h2>(.*?)</div>"));
        patterns.put(TemplateSection.DISPUTE_RESOLUTION, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>5\\. Giải quyết tranh chấp</h2>(.*?)</div>"));
        patterns.put(TemplateSection.GENERAL_TERMS, Pattern.compile("(?s)<div class=\"section\">\\s*<h2>6\\. Điều khoản chung</h2>(.*?)</div>"));
        patterns.put(TemplateSection.SIGNATURES, Pattern.compile("(?s)<div class=\"section break-avoid\">\\s*<h2>7\\. Chữ ký các Bên</h2>(.*?)</div>"));
        patterns.put(TemplateSection.FOOTER, Pattern.compile("(?s)<div class=\"footer\">(.*?)</div>"));
        patterns.put(TemplateSection.STYLES, Pattern.compile("(?s)<style>(.*?)</style>"));
        patterns.put(TemplateSection.SCRIPTS, Pattern.compile("(?s)<script>(.*?)</script>"));
        return patterns;
    }

    /**
     * Lấy tất cả sections của template
     */
    public List<TemplateSectionResponse> getAllSections() {
        String templateContent = templateService.getTemplateContent();
        List<TemplateSectionResponse> sections = new ArrayList<>();

        for (TemplateSection section : TemplateSection.values()) {
            String content = extractSectionContent(templateContent, section);
            sections.add(new TemplateSectionResponse(
                    section.name(),
                    content,
                    section.getDescription(),
                    false // hasChanges sẽ được set khi có edit
            ));
        }

        return sections;
    }

    /**
     * Lấy content của một section cụ thể
     */
    public TemplateSectionResponse getSection(TemplateSection section) {
        String templateContent = templateService.getTemplateContent();
        String content = extractSectionContent(templateContent, section);

        return new TemplateSectionResponse(
                section.name(),
                content,
                section.getDescription(),
                false
        );
    }

    /**
     * Edit một section của template
     */
    public TemplateEditResponse editSection(TemplateEditRequest request) {
        try {
            String templateContent = templateService.getTemplateContent();
            String updatedContent = updateSectionContent(templateContent, request.section(), request.content());

            // Update template trong Azure
            templateService.updateTemplateContentChunked(updatedContent);

            // Generate preview
            String preview = generatePreview(updatedContent, request.section());

            return new TemplateEditResponse(
                    true,
                    "Section updated successfully",
                    request.section().name(),
                    preview
            );

        } catch (Exception e) {
            return new TemplateEditResponse(
                    false,
                    "Failed to update section: " + e.getMessage(),
                    request.section().name(),
                    null
            );
        }
    }

    /**
     * Preview template với data mẫu
     */
    public String previewTemplate() {
        String templateContent = templateService.getTemplateContent();
        return generateFullPreview(templateContent);
    }

    /**
     * Extract content của một section từ HTML
     */
    private String extractSectionContent(String templateContent, TemplateSection section) {
        Pattern pattern = SECTION_PATTERNS.get(section);
        if (pattern == null) {
            return "";
        }

        Matcher matcher = pattern.matcher(templateContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    /**
     * Update content của một section trong HTML
     */
    private String updateSectionContent(String templateContent, TemplateSection section, String newContent) {
        Pattern pattern = SECTION_PATTERNS.get(section);
        if (pattern == null) {
            throw new IllegalArgumentException("Invalid section: " + section);
        }

        Matcher matcher = pattern.matcher(templateContent);
        if (matcher.find()) {
            String replacement = matcher.group(0).replace(matcher.group(1), newContent);
            return templateContent.replace(matcher.group(0), replacement);
        }

        throw new IllegalArgumentException("Section not found in template: " + section);
    }

    /**
     * Generate preview cho một section
     */
    private String generatePreview(String templateContent, TemplateSection section) {
        // Simple preview - có thể enhance thêm
        String sectionContent = extractSectionContent(templateContent, section);
        return "<div class=\"preview-section\">" + sectionContent + "</div>";
    }

    /**
     * Generate full preview với sample data
     */
    private String generateFullPreview(String templateContent) {
        // Sample data để preview
        Map<String, Object> sampleData = Map.of(
                "contract", Map.of(
                        "number", "EVS-0001-2024",
                        "effectiveDate", "01/01/2024",
                        "endDate", "31/12/2024",
                        "termLabel", "12 tháng",
                        "location", "Hà Nội",
                        "signDate", "01/01/2024",
                        "status", "DRAFT"
                ),
                "group", Map.of("name", "EVShare Group"),
                "vehicle", Map.of(
                        "model", "VinFast VF 8 Plus",
                        "plate", "30A-123.45",
                        "vin", "RLVZZZ1EZBW000001"
                ),
                "finance", Map.of(
                        "vehiclePrice", "950,000,000 VND",
                        "depositAmount", "2,000,000 VND",
                        "targetAmount", "50,000,000 VND",
                        "contributionRule", "Theo tỷ lệ sở hữu"
                ),
                "owners", List.of(
                        Map.of("name", "Nguyễn Văn A", "share", 40),
                        Map.of("name", "Trần Thị B", "share", 35),
                        Map.of("name", "Lê Văn C", "share", 25)
                )
        );

        // Replace placeholders với sample data
        String preview = templateContent;
        for (Map.Entry<String, Object> entry : sampleData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> subEntry : subMap.entrySet()) {
                    String placeholder = "data." + key + "." + subEntry.getKey();
                    String replacement = subEntry.getValue().toString();
                    preview = preview.replaceAll("\\{\\{" + placeholder + "\\}\\}", replacement);
                }
            } else if (value instanceof List) {
                // Handle owners list
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> owners = (List<Map<String, Object>>) value;
                StringBuilder ownersHtml = new StringBuilder();
                for (Map<String, Object> owner : owners) {
                    ownersHtml.append("<div class=\"owner-item\">")
                            .append("<strong>").append(owner.get("name")).append("</strong> - ")
                            .append(owner.get("share")).append("%")
                            .append("</div>");
                }
                preview = preview.replaceAll("\\{\\{data\\.owners\\}\\}", ownersHtml.toString());
            }
        }

        return preview;
    }
}
