package com.group8.evcoownership.service;

import com.group8.evcoownership.enums.NotificationType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@evcoownership.com}")
    private String fromEmail;

    @Value("${app.email.from-name:EV Co-ownership System}")
    private String fromName;

    /**
     * Send booking confirmation email
     */
    public void sendBookingConfirmation(String toEmail, String userName, Map<String, Object> bookingData) {
        String subject = "Booking Confirmation - EV Co-ownership";
        String message = buildBookingEmailContent(userName, bookingData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Send contract notification email
     */
    public void sendContractNotification(String toEmail, String userName, NotificationType type, Map<String, Object> contractData) {
        String subject = getEmailSubject(type);
        String message = buildContractEmailContent(userName, type, contractData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Send payment notification email
     */
    public void sendPaymentNotification(String toEmail, String userName, NotificationType type, Map<String, Object> paymentData) {
        String subject = getEmailSubject(type);
        String message = buildPaymentEmailContent(userName, type, paymentData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Send group invitation email
     */
    public void sendGroupInvitation(String toEmail, String userName, Map<String, Object> groupData) {
        String subject = "Group Invitation - EV Co-ownership";
        String message = buildGroupInvitationEmailContent(userName, groupData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Send maintenance notification email
     */
    public void sendMaintenanceNotification(String toEmail, String userName, NotificationType type, Map<String, Object> maintenanceData) {
        String subject = getEmailSubject(type);
        String message = buildMaintenanceEmailContent(userName, type, maintenanceData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Send monthly report email
     */
    public void sendMonthlyReport(String toEmail, String userName, Map<String, Object> reportData) {
        String subject = "Monthly Report - EV Co-ownership";
        String message = buildMonthlyReportEmailContent(userName, reportData);
        sendSimpleEmail(toEmail, subject, message);
    }

    /**
     * Generic email sending method
     */
    private void sendSimpleEmail(String toEmail, String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(message, false); // Plain text, not HTML

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Build booking email content
     */
    private String buildBookingEmailContent(String userName, Map<String, Object> bookingData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");
        content.append("Your vehicle booking has been confirmed successfully.\n\n");
        content.append("Booking Details:\n");
        content.append("- Vehicle: ").append(bookingData.getOrDefault("vehicleName", "N/A")).append("\n");
        content.append("- License Plate: ").append(bookingData.getOrDefault("licensePlate", "N/A")).append("\n");
        content.append("- Start Time: ").append(bookingData.getOrDefault("startDateTime", "N/A")).append("\n");
        content.append("- End Time: ").append(bookingData.getOrDefault("endDateTime", "N/A")).append("\n");
        content.append("- Duration: ").append(bookingData.getOrDefault("totalDuration", "N/A")).append(" hours\n");
        content.append("- Status: ").append(bookingData.getOrDefault("status", "Confirmed")).append("\n\n");
        content.append("Please arrive on time to pick up the vehicle.\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Build contract email content
     */
    private String buildContractEmailContent(String userName, NotificationType type, Map<String, Object> contractData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");

        switch (type) {
            case CONTRACT_CREATED -> {
                content.append("A new co-ownership contract has been created for your group.\n\n");
                content.append("Contract Details:\n");
            }
            case CONTRACT_APPROVAL_PENDING -> {
                content.append("Your contract is pending approval from the system administrator.\n\n");
                content.append("Contract Details:\n");
            }
            case CONTRACT_APPROVED -> {
                content.append("Congratulations! Your co-ownership contract has been approved.\n\n");
                content.append("Contract Details:\n");
            }
            case CONTRACT_REJECTED -> {
                content.append("Your contract has been rejected. Please contact support for assistance.\n\n");
                content.append("Contract Details:\n");
            }
            case CONTRACT_EXPIRING -> {
                content.append("Your contract is expiring soon. Please renew to continue using the service.\n\n");
                content.append("Contract Details:\n");
            }
            case DEPOSIT_REQUIRED -> {
                content.append("You need to pay the deposit to complete your group membership.\n\n");
                content.append("Contract Details:\n");
            }
            default -> {
                content.append("Contract notification.\n\n");
                content.append("Contract Details:\n");
            }
        }

        content.append("- Group: ").append(contractData.getOrDefault("groupName", "N/A")).append("\n");
        content.append("- Start Date: ").append(contractData.getOrDefault("startDate", "N/A")).append("\n");
        content.append("- End Date: ").append(contractData.getOrDefault("endDate", "N/A")).append("\n");
        content.append("- Deposit Amount: ").append(contractData.getOrDefault("depositAmount", "N/A")).append("\n");
        content.append("- Status: ").append(contractData.getOrDefault("status", "N/A")).append("\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Build payment email content
     */
    private String buildPaymentEmailContent(String userName, NotificationType type, Map<String, Object> paymentData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");

        switch (type) {
            case PAYMENT_SUCCESS -> content.append("Your payment has been processed successfully!\n\n");
            case PAYMENT_FAILED -> content.append("Your payment was not successful. Please try again.\n\n");
            case PAYMENT_REMINDER -> content.append("You have an outstanding payment that needs to be completed.\n\n");
            case DEPOSIT_REQUIRED ->
                    content.append("You need to pay the deposit to complete your group membership.\n\n");
            case DEPOSIT_OVERDUE -> content.append("Your deposit payment is overdue. Please pay immediately.\n\n");
            case BOOKING_CANCELLED -> content.append("Your booking has been cancelled.\n\n");
            default -> content.append("Payment notification.\n\n");
        }

        content.append("Transaction Details:\n");
        content.append("- Transaction Code: ").append(paymentData.getOrDefault("transactionCode", "N/A")).append("\n");
        content.append("- Amount: ").append(paymentData.getOrDefault("amount", "N/A")).append("\n");
        content.append("- Payment Method: ").append(paymentData.getOrDefault("paymentMethod", "N/A")).append("\n");
        content.append("- Payment Type: ").append(paymentData.getOrDefault("paymentType", "N/A")).append("\n");
        content.append("- Date: ").append(paymentData.getOrDefault("paymentDate", "N/A")).append("\n");
        content.append("- Status: ").append(paymentData.getOrDefault("status", "N/A")).append("\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Build group invitation email content
     */
    private String buildGroupInvitationEmailContent(String userName, Map<String, Object> groupData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");
        content.append("You have been invited to join a co-ownership group!\n\n");
        content.append("Group Details:\n");
        content.append("- Group Name: ").append(groupData.getOrDefault("groupName", "N/A")).append("\n");
        content.append("- Description: ").append(groupData.getOrDefault("description", "N/A")).append("\n");
        content.append("- Current Members: ").append(groupData.getOrDefault("currentMembers", "N/A")).append("\n");
        content.append("- Deposit Amount: ").append(groupData.getOrDefault("depositAmount", "N/A")).append("\n");
        content.append("- Status: ").append(groupData.getOrDefault("status", "Active")).append("\n\n");
        content.append("Please respond to this invitation within 7 days.\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Build maintenance email content
     */
    private String buildMaintenanceEmailContent(String userName, NotificationType type, Map<String, Object> maintenanceData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");

        switch (type) {
            case MAINTENANCE_REQUESTED ->
                    content.append("A maintenance request has been submitted for your vehicle.\n\n");
            case MAINTENANCE_APPROVED -> content.append("Your maintenance request has been approved.\n\n");
            case MAINTENANCE_COMPLETED -> content.append("The maintenance work has been completed.\n\n");
            case MAINTENANCE_OVERDUE -> content.append("The maintenance is overdue. Please schedule immediately.\n\n");
            case DEPOSIT_OVERDUE -> content.append("Your deposit payment is overdue. Please pay immediately.\n\n");
            default -> content.append("Maintenance notification.\n\n");
        }

        content.append("Maintenance Details:\n");
        content.append("- Vehicle: ").append(maintenanceData.getOrDefault("vehicleName", "N/A")).append("\n");
        content.append("- Description: ").append(maintenanceData.getOrDefault("description", "N/A")).append("\n");
        content.append("- Estimated Cost: ").append(maintenanceData.getOrDefault("estimatedCost", "N/A")).append("\n");
        content.append("- Status: ").append(maintenanceData.getOrDefault("status", "N/A")).append("\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Build monthly report email content
     */
    private String buildMonthlyReportEmailContent(String userName, Map<String, Object> reportData) {
        StringBuilder content = new StringBuilder();
        content.append("Dear ").append(userName).append(",\n\n");
        content.append("Your monthly usage and payment report is ready.\n\n");
        content.append("Report Summary:\n");
        content.append("- Total Usage Hours: ").append(reportData.getOrDefault("totalHours", "N/A")).append("\n");
        content.append("- Total Payments: ").append(reportData.getOrDefault("totalPayments", "N/A")).append("\n");
        content.append("- Maintenance Costs: ").append(reportData.getOrDefault("maintenanceCosts", "N/A")).append("\n");
        content.append("- Report Period: ").append(reportData.getOrDefault("period", "N/A")).append("\n\n");
        content.append("Best regards,\nEV Co-ownership Team");
        return content.toString();
    }

    /**
     * Get email subject based on notification type
     */
    private String getEmailSubject(NotificationType type) {
        return switch (type) {
            case CONTRACT_CREATED -> "Contract Created - EV Co-ownership";
            case CONTRACT_APPROVAL_PENDING -> "Contract Pending Approval - EV Co-ownership";
            case CONTRACT_APPROVED -> "Contract Approved - EV Co-ownership";
            case CONTRACT_REJECTED -> "Contract Rejected - EV Co-ownership";
            case CONTRACT_EXPIRING -> "Contract Expiring - EV Co-ownership";
            case PAYMENT_SUCCESS -> "Payment Successful - EV Co-ownership";
            case PAYMENT_FAILED -> "Payment Failed - EV Co-ownership";
            case PAYMENT_REMINDER -> "Payment Reminder - EV Co-ownership";
            case DEPOSIT_REQUIRED -> "Deposit Required - EV Co-ownership";
            case DEPOSIT_OVERDUE -> "Deposit Overdue - EV Co-ownership";
            case MAINTENANCE_REQUESTED -> "Maintenance Requested - EV Co-ownership";
            case MAINTENANCE_APPROVED -> "Maintenance Approved - EV Co-ownership";
            case MAINTENANCE_COMPLETED -> "Maintenance Completed - EV Co-ownership";
            case MAINTENANCE_OVERDUE -> "Maintenance Overdue - EV Co-ownership";
            case GROUP_INVITATION -> "Group Invitation - EV Co-ownership";
            case GROUP_MEMBER_JOINED -> "New Member Joined - EV Co-ownership";
            case GROUP_MEMBER_LEFT -> "Member Left Group - EV Co-ownership";
            case GROUP_STATUS_CHANGED -> "Group Status Changed - EV Co-ownership";
            case FUND_LOW_BALANCE -> "Low Fund Balance - EV Co-ownership";
            case FUND_CONTRIBUTION_REQUIRED -> "Fund Contribution Required - EV Co-ownership";
            case FUND_EXPENSE_APPROVED -> "Fund Expense Approved - EV Co-ownership";
            case VEHICLE_EMERGENCY -> "Vehicle Emergency - EV Co-ownership";
            case SYSTEM_MAINTENANCE -> "System Maintenance - EV Co-ownership";
            case SECURITY_ALERT -> "Security Alert - EV Co-ownership";
            case POLICY_UPDATE -> "Policy Update - EV Co-ownership";
            default -> "Notification - EV Co-ownership";
        };
    }
}
