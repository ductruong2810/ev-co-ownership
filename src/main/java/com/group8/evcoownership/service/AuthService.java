package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Role;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.OtpType;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.exception.InvalidCredentialsException;
import com.group8.evcoownership.repository.RoleRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.utils.JwtUtil;
import com.group8.evcoownership.utils.OtpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class AuthService {

    // Logger dùng để ghi log luồng xử lý (thành công, cảnh báo, lỗi)
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Repository làm việc với bảng Users (CRUD, truy vấn theo email, v.v.)
    @Autowired
    private UserRepository userRepository;

    // Repository làm việc với bảng Roles (tìm role theo RoleName)
    @Autowired
    private RoleRepository roleRepository;

    // Tiện ích phát sinh/kiểm tra/giải mã JWT (access token, refresh token)
    @Autowired
    private JwtUtil jwtUtil;

    // Dùng mã hóa mật khẩu, so khớp mật khẩu an toàn (BCrypt/Delegating encoder)
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Tiện ích OTP: tạo, xác thực, theo dõi số lần thử và thời hạn
    @Autowired
    private OtpUtil otpUtil;

    // Service gửi email OTP/notification đến người dùng
    @Autowired
    private EmailService emailService;

    // Service lấy thông tin hồ sơ người dùng (profile) sau khi đăng ký/đăng nhập
    @Autowired
    private UserProfileService userProfileService;

    // Bản đồ OTP -> email cho flow đăng ký: tra cứu email bằng OTP nhập
    private final Map<String, String> registerOtpToEmailMap = new ConcurrentHashMap<>();
    // Lưu tạm thông tin đăng ký (pending) cho đến khi OTP verify
    private final Map<String, RegisterRequestDTO> pendingUsers = new ConcurrentHashMap<>();
    // Lưu trạng thái yêu cầu quên mật khẩu theo email (đang chờ OTP)
    private final Map<String, String> pendingPasswordResets = new ConcurrentHashMap<>();
    // Bản đồ OTP -> email cho flow quên mật khẩu
    private final Map<String, String> otpToEmailMap = new ConcurrentHashMap<>();
    // Token reset mật khẩu -> email (để đổi mật khẩu sau khi xác thực OTP)
    private final Map<String, String> resetTokens = new ConcurrentHashMap<>();

    // ================= LOGIN =================
    public LoginResponseDTO login(LoginRequestDTO request) {
        // Tìm user theo email; nếu không tồn tại, ném lỗi thông tin đăng nhập sai (tránh lộ user tồn tại)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email or password is incorrect"));

        // So khớp mật khẩu plaintext từ request với password hash trong DB (không so sánh thô)
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email or password is incorrect");
        }

        // Chỉ cho đăng nhập khi tài khoản đang ACTIVE
        // nếu chưa kích hoạt, báo lỗi rõ ràng
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not activated. Please verify your email");
        }

        //BỎ
//        // Ghi nhận lựa chọn rememberMe để quyết định TTL của refresh token
//        boolean rememberMe = request.isRememberMe();

        // Tạo access token và refresh token; trả role hiện tại của user
        return LoginResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .role(user.getRole().getRoleName().name())
                .build();
    }

//    // ================= REFRESH TOKEN =================
//    public LoginResponseDTO refreshToken(String refreshToken) {
//        log.info("Processing token refresh");
//
//        // Kiểm tra token rỗng/null: dùng trim() để loại khoảng trắng vô tình -> đảm bảo không nhận chuỗi trống trá hình
//        if (refreshToken == null || refreshToken.trim().isEmpty()) {
//            throw new IllegalArgumentException("Refresh token cannot be empty");
//        }
//
//        // Xác thực refresh token: chữ ký, thời hạn, issuer, v.v.; nếu fail => ném lỗi
//        if (!jwtUtil.validateToken(refreshToken)) {
//            throw new IllegalArgumentException("Refresh token is invalid or has expired");
//        }
//
//        // Trích email từ refresh token đã validate
//        String email = jwtUtil.extractEmail(refreshToken);
//
//        // Lấy user theo email; nếu không có => dữ liệu không nhất quán hoặc token không hợp lệ
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));
//
//        // Chỉ cấp token mới nếu tài khoản ACTIVE
//        if (user.getStatus() != UserStatus.ACTIVE) {
//            throw new IllegalStateException("Account is not activated");
//        }
//
//        // Phát hành cặp token mới (access + refresh)
//        String newAccessToken = jwtUtil.generateToken(user);
//      String newRefreshToken = jwtUtil.generateRefreshToken(user);
//
//        log.info("Tokens refreshed successfully for user: {}", email);
//
//        // Trả về response chứa token mới và role
//        return LoginResponseDTO.builder()
//                .accessToken(newAccessToken)
//                .role(user.getRole().getRoleName().name())
//                .build();
//    }

    // ================= REQUEST OTP (REGISTRATION) =================
    public OtpResponseDTO requestOtp(RegisterRequestDTO request) {
        // Lấy email đăng ký từ DTO để dùng xuyên suốt
        String email = request.getEmail();

        // Không cho đăng ký nếu email đã tồn tại (tránh trùng tài khoản)
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Bảo đảm người dùng nhập trùng mật khẩu/confirm mật khẩu
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        // Biến tạm giữ OTP để rollback nếu gửi mail thất bại
        String otp = null;

        try {
            // Tạo OTP gắn với email (OTPUtil sẽ quản lý TTL và số lần nhập sai)
            otp = otpUtil.generateOtp(email);
            // Gửi email OTP đến người dùng kèm họ tên để cá nhân hóa nội dung
            emailService.sendOtpEmail(email, request.getFullName(), otp);
            // Lưu trạng thái đăng ký pending để dùng khi verify OTP
            pendingUsers.put(email, request);
            // Lưu map OTP -> email để tra ngược khi người dùng chỉ nhập OTP
            registerOtpToEmailMap.put(otp, email);

            log.info("Registration OTP sent to email: {}", email);

            // Trả thông điệp và thời hạn OTP (300 giây = 5 phút)
            return OtpResponseDTO.builder()
                    .email(email)
                    .message("OTP has been sent to your email")
                    .type(OtpType.REGISTRATION)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            // Nếu có lỗi (gửi mail/OTP), rollback các map và vô hiệu OTP nếu đã tạo
            log.error("Failed to send registration OTP to {}: {}", email, e.getMessage());

            if (otp != null) {
                otpUtil.invalidateOtp(email); // Hủy OTP để tránh OTP mồ côi còn hiệu lực
            }
            pendingUsers.remove(email); // Xóa trạng thái pending để user có thể thử lại sạch
            if (otp != null) {
                registerOtpToEmailMap.remove(otp); // Xóa ánh xạ OTP đã tạo
            }

            throw new RuntimeException("Unable to send verification email. Please try again later");
        }
    }

    // ================= VERIFY OTP =================
    public VerifyOtpResponseDTO verifyOtp(String otp, OtpType type) {
        log.info("Verifying OTP with type: {}", type);

        // Chặn OTP null/trắng; trim() tránh chuỗi toàn khoảng trắng
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP cannot be empty");
        }

        // Bắt buộc có type để điều hướng đúng flow
        if (type == null) {
            throw new IllegalArgumentException("OTP type is required");
        }

        try {
            // Điều hướng theo loại OTP để gọi hàm verify chuyên biệt
            if (type == OtpType.REGISTRATION) {
                log.info("Verifying REGISTRATION OTP");
                return verifyRegistrationOtp(otp);
            } else if (type == OtpType.PASSWORD_RESET) {
                log.info("Verifying PASSWORD_RESET OTP");
                return verifyPasswordResetOtp(otp);
            } else {
                // Bắt case enum không hợp lệ (phòng sai input phía client)
                throw new IllegalArgumentException("Invalid OTP type");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Ném lại các lỗi hợp lệ để client nhận đúng thông điệp
            throw e;
        } catch (Exception e) {
            // Bắt mọi lỗi không lường trước, ghi log đầy đủ stacktrace để dễ điều tra
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("An error occurred during verification. Please try again");
        }
    }

    // ================= VERIFY REGISTRATION OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyRegistrationOtp(String otp) {
        log.info("Verifying registration OTP");

        // Tra cứu email bằng OTP (nếu null => OTP hết hạn/không tồn tại)
        String email = registerOtpToEmailMap.get(otp);
        if (email == null) {
            log.error("Registration OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP is invalid or has expired");
        }

        // Lấy lại thông tin đăng ký pending theo email (phòng trường hợp map bị mất đồng bộ)
        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.error("No pending registration found for email: {}", email);
            registerOtpToEmailMap.remove(otp); // Xóa ánh xạ OTP mồ côi
            throw new IllegalStateException("Registration information not found. Please request a new OTP");
        }

        // Xác thực OTP: đúng giá trị, còn hạn, chưa vượt quá số lần thử
        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            // Nếu sai OTP: trả về số lần còn lại để UX tốt hơn
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                // Hết lượt: hủy trạng thái pending và ánh xạ OTP để user đăng ký lại
                pendingUsers.remove(email);
                registerOtpToEmailMap.remove(otp);
                log.error("Registration OTP verification failed - no attempts remaining");
                throw new IllegalStateException("You have entered an incorrect OTP too many times. Please request a new OTP");
            }
            log.error("Invalid registration OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("Invalid OTP. You have " + remainingAttempts + " attempts remaining");
        }

        // OTP hợp lệ: tạo user thực trong DB
        User user = createUser(request);
        // Xóa trạng thái pending và ánh xạ OTP để tránh reuse
        pendingUsers.remove(email);
        registerOtpToEmailMap.remove(otp);

        // Đăng nhập tự động sau đăng ký: phát hành access/refresh token
        String accessToken = jwtUtil.generateToken(user);
//        String refreshToken = jwtUtil.generateRefreshToken(user, false);

        // Lấy thông tin profile để trả về front-end
        UserProfileResponseDTO userProfile = userProfileService.getUserProfile(user.getEmail());

        log.info("User registered successfully: {}", email);

        // Trả kết quả xác thực kèm token và thông tin user
        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("Account registration successful! You have been automatically logged in")
                .type(OtpType.REGISTRATION)
                .accessToken(accessToken)
                .user(userProfile)
                .build();
    }

    // ================= VERIFY PASSWORD RESET OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyPasswordResetOtp(String otp) {
        log.info("Verifying password reset OTP");

        // Dùng OTP để tra email (nếu null => OTP không tồn tại/hết hạn/đã bị thay thế)
        String email = otpToEmailMap.get(otp);
        if (email == null) {
            log.error("Password reset OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP is invalid or has expired");
        }

        // Đảm bảo email này đang trong trạng thái chờ reset (đã gửi OTP trước đó)
        if (!pendingPasswordResets.containsKey(email)) {
            log.error("No pending password reset found for email: {}", email);
            otpToEmailMap.remove(otp); // Xóa ánh xạ không hợp lệ
            throw new IllegalStateException("Password reset request not found. Please request a new OTP");
        }

        // Xác thực OTP (đúng/đủ hạn/đủ lượt)
        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                // Hết lượt: hủy pending và xóa ánh xạ OTP để buộc quy trình bắt đầu lại
                pendingPasswordResets.remove(email);
                otpToEmailMap.remove(otp);
                log.error("Password reset OTP verification failed - no attempts remaining");
                throw new IllegalStateException("You have entered an incorrect OTP too many times. Please request a new OTP");
            }
            log.error("Invalid password reset OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("Invalid OTP. You have " + remainingAttempts + " attempts remaining");
        }

        // OTP hợp lệ: tạo reset token dùng một lần để đổi mật khẩu
        String resetToken = generateResetToken();
        resetTokens.put(resetToken, email);
        // Kết thúc trạng thái pending và xóa ánh xạ OTP để tránh reuse
        pendingPasswordResets.remove(email);
        otpToEmailMap.remove(otp);

        log.info("Reset OTP verified successfully: {}", email);

        // Trả reset token cho client để gọi API đổi mật khẩu bước tiếp theo
        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("OTP verification successful. Please set your new password")
                .type(OtpType.PASSWORD_RESET)
                .resetToken(resetToken)
                .build();
    }

    // ================= CREATE USER =================
    private User createUser(RegisterRequestDTO request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Lấy role mặc định cho người đồng sở hữu (CO_OWNER); nếu thiếu => cấu hình hệ thống sai
        Role coOwnerRole = roleRepository.findByRoleName(RoleName.CO_OWNER)
                .orElseThrow(() -> {
                    log.error("Role CO_OWNER not found in database");
                    return new IllegalStateException("System configuration error. Please contact the administrator");
                });

        // Dựng entity User từ request; password được hash an toàn trước khi lưu
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhone())
                .role(coOwnerRole)
                .status(UserStatus.ACTIVE) // Kích hoạt ngay sau khi verify OTP
                .build();

        // Lưu user vào DB và trả về entity đã lưu (có ID)
        return userRepository.save(user);
    }

    // ================= RESEND OTP =================
    public OtpResponseDTO resendOtp(String email, OtpType type) {
        log.info("Resending {} OTP for email: {}", type, email);

        // Điều hướng theo loại OTP để gửi lại đúng quy trình
        if (type == OtpType.REGISTRATION) {
            return resendRegistrationOtp(email);
        } else if (type == OtpType.PASSWORD_RESET) {
            return resendPasswordResetOtp(email);
        } else {
            throw new IllegalArgumentException("Invalid OTP type");
        }
    }

    // ================= RESEND REGISTRATION OTP (PRIVATE) =================
    private OtpResponseDTO resendRegistrationOtp(String email) {
        log.info("Resending registration OTP for email: {}", email);

        // Chỉ cho resend nếu đang có pending đăng ký
        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.warn("Resend OTP attempt for non-pending email: {}", email);
            throw new IllegalStateException("Registration information not found. Please register again from the beginning");
        }

        // Biến giữ OTP mới để rollback nếu gửi thất bại
        String newOtp = null;

        try {
            // Tạo OTP mới và gửi mail; cho phép thay thế OTP trước đó
            newOtp = otpUtil.generateOtp(email);
            emailService.sendOtpEmail(email, request.getFullName(), newOtp);
            // Cập nhật ánh xạ OTP mới -> email để verify về sau
            registerOtpToEmailMap.put(newOtp, email);

            log.info("Registration OTP resent successfully to email: {}", email);

            // Trả thông tin OTP mới cho client (hạn 5 phút)
            return OtpResponseDTO.builder()
                    .email(email)
                    .message("A new OTP has been sent to your email")
                    .type(OtpType.REGISTRATION)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            // Gửi thất bại: hủy OTP vừa tạo (nếu có) để tránh lộ OTP hoạt động nhưng user không nhận được
            log.error("Failed to resend registration OTP to email {}: {}", email, e.getMessage());
            if (newOtp != null) {
                otpUtil.invalidateOtp(email);
            }

            throw new RuntimeException("Unable to resend OTP. Please try again later");
        }
    }

    // ================= RESEND PASSWORD RESET OTP (PRIVATE) =================
    private OtpResponseDTO resendPasswordResetOtp(String email) {
        log.info("Resending password reset OTP for email: {}", email);

        // Chỉ cho resend nếu đang có pending reset
        if (!pendingPasswordResets.containsKey(email)) {
            log.warn("Resend password reset OTP attempt for non-pending reset: {}", email);
            throw new IllegalStateException("Password reset request not found. Please request again from the beginning");
        }

        try {
            // Tạo OTP mới và thay thế mọi ánh xạ cũ cho email này để tránh OTP cũ còn hiệu lực
            String newOtp = otpUtil.generateOtp(email);
            otpToEmailMap.values().removeIf(e -> e.equals(email)); // Xóa mọi OTP cũ trỏ về email
            otpToEmailMap.put(newOtp, email); // Lưu OTP mới
            emailService.sendPasswordResetOtpEmail(email, newOtp); // Gửi OTP mới

            log.info("Password reset OTP resent successfully to: {}", email);

            // Trả thông tin OTP mới cho client
            return OtpResponseDTO.builder()
                    .email(email)
                    .message("A new OTP has been sent to your email")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (RuntimeException e) {
            // RuntimeException được throw tiếp để giữ nguyên thông điệp cụ thể (nếu có)
            log.error("Failed to resend password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            // Bắt các lỗi còn lại, ghi log đầy đủ và trả thông báo tổng quát
            log.error("Unexpected error while resending password reset OTP to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Unable to resend OTP. Please try again later");
        }
    }

    // ================= FORGOT PASSWORD =================
    public OtpResponseDTO forgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        // Tìm user theo email; nếu không có, trả lỗi có kiểm soát (không lộ thông tin thừa)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Forgot password attempt for non-existent email: {}", email);
                    return new IllegalArgumentException("Email does not exist in the system");
                });

        // Chỉ xử lý quên mật khẩu nếu tài khoản ACTIVE
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Forgot password attempt for inactive account: {}", email);
            throw new IllegalStateException("Account is not activated. Please contact the administrator");
        }

        // Biến tạm lưu OTP để rollback nếu gửi mail lỗi
        String otp = null;

        try {
            // Tạo OTP và gửi đến email, đồng thời đánh dấu pending reset
            otp = otpUtil.generateOtp(email);
            emailService.sendPasswordResetOtpEmail(email, otp);
            pendingPasswordResets.put(email, email); // Đánh dấu email đang ở trạng thái reset
            otpToEmailMap.put(otp, email); // Lưu OTP -> email

            log.info("Password reset OTP sent successfully to: {}", email);

            // Trả thông tin OTP và thời hạn
            return OtpResponseDTO.builder()
                    .email(email)
                    .message("OTP has been sent to your email")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            // Gửi OTP thất bại: rollback các map và vô hiệu OTP nếu đã tạo
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());

            if (otp != null) {
                otpUtil.invalidateOtp(email); // Hủy OTP theo email
            }
            pendingPasswordResets.remove(email); // Hủy trạng thái pending reset
            if (otp != null) {
                otpToEmailMap.remove(otp); // Xóa ánh xạ OTP đã chèn
            }

            throw new RuntimeException("Unable to send OTP. Please try again later");
        }
    }

    // ================= RESET PASSWORD WITH TOKEN =================
    public ResetPasswordResponseDTO resetPasswordWithToken(ResetPasswordRequestDTO request) {
        // Lấy reset token từ request để xác định email tương ứng
        String resetToken = request.getResetToken();
        log.info("Processing password reset with token");

        // Kiểm tra reset token rỗng/null; dùng trim() để tránh token chỉ toàn khoảng trắng
        if (resetToken == null || resetToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Reset token cannot be empty");
        }
        // Xác thực hai trường mật khẩu mới trùng khớp
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        try {
            // Tra cứu email từ reset token; nếu không có => token hết hạn/không hợp lệ
            String email = resetTokens.get(resetToken);
            if (email == null) {
                log.error("Invalid or expired reset token");
                throw new IllegalArgumentException("Reset token is invalid or has expired. Please request a new OTP");
            }

            // Lấy user cần reset mật khẩu; nếu không tồn tại => dữ liệu không nhất quán
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Account not found"));

            // Cập nhật password hash bằng password mới an toàn (mã hóa)
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user); // Lưu thay đổi

            // Loại bỏ token sau khi đã dùng (one-time token)
            resetTokens.remove(resetToken);

            // Đăng nhập tự động sau khi reset mật khẩu thành công
            String accessToken = jwtUtil.generateToken(user);

            log.info("Password reset successfully for email: {} - Auto login enabled", email);

            // Trả thông điệp và access token mới
            return ResetPasswordResponseDTO.builder()
                    .message("Password reset successful! You have been automatically logged in")
                    .accessToken(accessToken)
                    .build();

        } catch (IllegalArgumentException e) {
            // Trả lại lỗi hợp lệ để client nhận thông điệp chính xác
            throw e;
        } catch (Exception e) {
            // Lỗi không lường trước: ghi log chi tiết và trả lỗi tổng quát
            log.error("Unexpected error during password reset: {}", e.getMessage(), e);
            throw new RuntimeException("An error occurred during password reset. Please try again");
        }
    }

    // ================= CHANGE PASSWORD =================
    public String changePassword(String email, ChangePasswordRequestDTO request) {
        log.info("Processing change password request for email: {}", email);

        // Kiểm tra email đầu vào null/rỗng; dùng trim() để từ chối chuỗi toàn khoảng trắng
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        // Xác thực password mới và confirm trùng nhau
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        try {
            // Tìm user theo email; nếu không thấy => báo lỗi
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.error("User not found for email: {}", email);
                        return new IllegalArgumentException("Account not found");
                    });

            // Kiểm tra mật khẩu cũ có khớp với hash trong DB không
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                log.warn("Invalid old password for email: {}", email);
                throw new IllegalArgumentException("Old password is incorrect");
            }

            // Không cho đặt mật khẩu mới trùng mật khẩu cũ (tăng bảo mật/giảm rủi ro tái sử dụng)
            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                log.warn("New password same as old password for email: {}", email);
                throw new IllegalArgumentException("New password must be different from old password");
            }

            // Mã hóa và lưu mật khẩu mới
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            log.info("Password changed successfully for email: {}", email);
            return "Password changed successfully!";

        } catch (IllegalArgumentException e) {
            // Ném lại lỗi hợp lệ để client nhận đúng thông điệp
            throw e;
        } catch (Exception e) {
            // Lỗi bất ngờ: log đầy đủ và trả thông điệp tổng quát
            log.error("Unexpected error during password change for email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("An error occurred during password change. Please try again");
        }
    }

    // ================= HELPER =================
    private String generateResetToken() {
        // Phát sinh chuỗi UUID ngẫu nhiên làm reset token (đủ độ dài/entropy cơ bản cho mục đích này)
        return java.util.UUID.randomUUID().toString();
    }
}
