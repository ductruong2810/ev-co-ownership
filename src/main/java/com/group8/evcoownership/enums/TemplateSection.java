package com.group8.evcoownership.enums;

import lombok.Getter;

@Getter
public enum TemplateSection {
    HEADER("Header", "Contract header and title section"),
    VEHICLE_INFO("Vehicle Info", "Vehicle information section"),
    FINANCE_TERMS("Finance Terms", "Financial terms and deposit information"),
    USAGE_RULES("Usage Rules", "Vehicle usage rules and regulations"),
    MAINTENANCE("Maintenance", "Maintenance and repair procedures"),
    DISPUTE_RESOLUTION("Dispute Resolution", "Dispute resolution procedures"),
    GENERAL_TERMS("General Terms", "General contract terms and conditions"),
    SIGNATURES("Signatures", "Signature section for contract parties"),
    FOOTER("Footer", "Contract footer and additional information"),
    STYLES("Styles", "CSS styles and formatting"),
    SCRIPTS("Scripts", "JavaScript functions and logic");

    private final String displayName;
    private final String description;

    TemplateSection(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
