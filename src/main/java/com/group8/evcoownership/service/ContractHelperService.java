package com.group8.evcoownership.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractHelperService {

    /**
     * Format date
     */
    public String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Format currency to Vietnamese format
     */
    public String formatCurrency(java.math.BigDecimal amount) {
        if (amount == null) return "0 VND";
        return String.format("%,.0f VND", amount.doubleValue());
    }

    /**
     * Generate contract number
     */
    public String generateContractNumber(Long contractId) {
        return "EVS-" + String.format("%04d", contractId) + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
    }

    /**
     * Tính toán kỳ hạn hợp đồng từ startDate và endDate
     * Trả về string mô tả kỳ hạn (ví dụ: "1 year", "6 months", "2 years 3 months", "90 days")
     */
    public String calculateTermLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return "N/A";
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
        long years = java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate);

        if (years > 0) {
            long remainingMonths = months % 12;
            if (remainingMonths == 0) {
                return years + (years == 1 ? " year" : " years");
            } else {
                return years + (years == 1 ? " year" : " years") + " " + remainingMonths + (remainingMonths == 1 ? " month" : " months");
            }
        } else if (months > 0) {
            return months + (months == 1 ? " month" : " months");
        } else {
            return days + (days == 1 ? " day" : " days");
        }
    }

    /**
     * Cập nhật term trong contract terms (nếu có)
     */
    public String updateTermInTerms(String terms, LocalDate startDate, LocalDate endDate) {
        if (terms == null || terms.isEmpty() || startDate == null || endDate == null) {
            return terms;
        }

        // Tính toán term mới
        String newTermLabel = calculateTermLabel(startDate, endDate);

        String noteSuffix = " (minimum 1 month, maximum 5 years)";
        Pattern pattern = Pattern.compile("(?i)(Kỳ hạn|Term|Period):\\s*[^\\n]+");
        Matcher matcher = pattern.matcher(terms);

        if (matcher.find()) {
            String label = matcher.group(1);
            String replacement = label + ": " + newTermLabel + noteSuffix;
            return matcher.replaceAll(replacement);
        }

        // Nếu không tìm thấy pattern, append term line vào đầu
        return "Term: " + newTermLabel + noteSuffix + "\n" + terms;
    }

    /**
     * Cập nhật deposit amount trong contract terms
     */
    public String updateDepositAmountInTerms(String terms, java.math.BigDecimal newDepositAmount) {
        if (terms == null || terms.isEmpty()) {
            return terms;
        }

        // Tìm và thay thế dòng "- Deposit amount: ..."
        String depositPattern = "- Deposit amount:.*";
        String replacement = "- Deposit amount: " + formatCurrency(newDepositAmount);

        return terms.replaceAll(depositPattern, replacement);
    }

    /**
     * Loại bỏ phần AUTO-SIGNED và LEGAL INFORMATION khỏi contract terms
     * Để tránh lặp lại khi ký lại contract
     */
    public String removeAutoSignatureSection(String terms) {
        if (terms == null || terms.isEmpty()) {
            return terms;
        }

        // Pattern để tìm và loại bỏ phần [AUTO-SIGNED] và [LEGAL INFORMATION]
        // Match từ [AUTO-SIGNED] đến hết phần [LEGAL INFORMATION] và tất cả nội dung sau đó đến cuối chuỗi
        // hoặc đến khi gặp 2 dòng trống liên tiếp (đánh dấu phần mới)
        Pattern pattern = Pattern.compile(
                "\\s*\\[AUTO-SIGNED].*?\\[LEGAL INFORMATION].*?(?=\\n\\s*\\n|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        String cleaned = pattern.matcher(terms).replaceAll("").trim();

        // Loại bỏ các dòng trống thừa (3 dòng trống trở lên thành 2 dòng trống)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
}

