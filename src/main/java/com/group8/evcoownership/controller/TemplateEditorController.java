package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.TemplateEditRequest;
import com.group8.evcoownership.dto.TemplateEditResponse;
import com.group8.evcoownership.dto.TemplateSectionResponse;
import com.group8.evcoownership.enums.TemplateSection;
import com.group8.evcoownership.service.TemplateEditorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/template/editor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TemplateEditorController {

    private final TemplateEditorService templateEditorService;

    /**
     * Lấy tất cả sections của template
     */
    @GetMapping("/sections")
    public ResponseEntity<List<TemplateSectionResponse>> getAllSections() {
        List<TemplateSectionResponse> sections = templateEditorService.getAllSections();
        return ResponseEntity.ok(sections);
    }

    /**
     * Lấy content của một section cụ thể
     */
    @GetMapping("/sections/{section}")
    public ResponseEntity<TemplateSectionResponse> getSection(@PathVariable TemplateSection section) {
        TemplateSectionResponse sectionResponse = templateEditorService.getSection(section);
        return ResponseEntity.ok(sectionResponse);
    }

    /**
     * Edit một section của template
     */
    @PostMapping("/sections/{section}")
    public ResponseEntity<TemplateEditResponse> editSection(
            @PathVariable TemplateSection section,
            @Valid @RequestBody TemplateEditRequest request) {

        // Ensure section matches path variable
        if (request.section() != section) {
            throw new IllegalArgumentException("Section mismatch between path and request body");
        }

        TemplateEditResponse response = templateEditorService.editSection(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Preview template với sample data
     */
    @GetMapping("/preview")
    public ResponseEntity<String> previewTemplate() {
        String preview = templateEditorService.previewTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return ResponseEntity.ok()
                .headers(headers)
                .body(preview);
    }

    /**
     * Lấy danh sách tất cả sections có thể edit
     */
    @GetMapping("/sections-info")
    public ResponseEntity<List<TemplateSectionInfo>> getSectionsInfo() {
        List<TemplateSectionInfo> sectionsInfo = List.of(
                new TemplateSectionInfo(TemplateSection.HEADER.name(), TemplateSection.HEADER.getDisplayName(), TemplateSection.HEADER.getDescription()),
                new TemplateSectionInfo(TemplateSection.VEHICLE_INFO.name(), TemplateSection.VEHICLE_INFO.getDisplayName(), TemplateSection.VEHICLE_INFO.getDescription()),
                new TemplateSectionInfo(TemplateSection.FINANCE_TERMS.name(), TemplateSection.FINANCE_TERMS.getDisplayName(), TemplateSection.FINANCE_TERMS.getDescription()),
                new TemplateSectionInfo(TemplateSection.USAGE_RULES.name(), TemplateSection.USAGE_RULES.getDisplayName(), TemplateSection.USAGE_RULES.getDescription()),
                new TemplateSectionInfo(TemplateSection.MAINTENANCE.name(), TemplateSection.MAINTENANCE.getDisplayName(), TemplateSection.MAINTENANCE.getDescription()),
                new TemplateSectionInfo(TemplateSection.DISPUTE_RESOLUTION.name(), TemplateSection.DISPUTE_RESOLUTION.getDisplayName(), TemplateSection.DISPUTE_RESOLUTION.getDescription()),
                new TemplateSectionInfo(TemplateSection.GENERAL_TERMS.name(), TemplateSection.GENERAL_TERMS.getDisplayName(), TemplateSection.GENERAL_TERMS.getDescription()),
                new TemplateSectionInfo(TemplateSection.SIGNATURES.name(), TemplateSection.SIGNATURES.getDisplayName(), TemplateSection.SIGNATURES.getDescription()),
                new TemplateSectionInfo(TemplateSection.FOOTER.name(), TemplateSection.FOOTER.getDisplayName(), TemplateSection.FOOTER.getDescription()),
                new TemplateSectionInfo(TemplateSection.STYLES.name(), TemplateSection.STYLES.getDisplayName(), TemplateSection.STYLES.getDescription()),
                new TemplateSectionInfo(TemplateSection.SCRIPTS.name(), TemplateSection.SCRIPTS.getDisplayName(), TemplateSection.SCRIPTS.getDescription())
        );

        return ResponseEntity.ok(sectionsInfo);
    }

    /**
     * Inner class for section info
     */
    public record TemplateSectionInfo(
            String name,
            String displayName,
            String description
    ) {
    }
}
