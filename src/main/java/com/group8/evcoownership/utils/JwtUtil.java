package com.group8.evcoownership.utils;

import com.group8.evcoownership.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;
    // Chuỗi bí mật dùng để ký và verify JWT, lấy từ file cấu hình

    @Value("${jwt.expiration}")
    private long expiration;
    // Thời gian sống của access token (tính bằng millisecon)

//    @Value("${jwt.refresh-expiration}")
//    private long refreshExpiration;
//    // Thời gian sống của refresh token bình thường (ví dụ 7 ngày)

//    @Value("${jwt.remember-me-expiration}")
//    private long rememberMeExpiration;
//    // Thời gian sống của refresh token khi user chọn Remember Me (ví dụ 30 ngày)


    // ========= LẤY userId TỪ TOKEN =========
    // Extract userId từ token
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())   // cấu hình key bí mật để verify chữ ký token
                .build()
                .parseClaimsJws(token)            // parse và verify token
                .getBody();                       // lấy phần payload (claims) trong JWT

        Object userIdObj = claims.get("userId");  // lấy claim userId mà mình đã nhét vào khi generate token
        if (userIdObj == null) {
            throw new IllegalArgumentException("Invalid userId in token");
        }

        return Long.valueOf(userIdObj.toString()); // convert về Long để dùng trong service
    }


    // ========= TẠO ACCESS TOKEN =========
    //Generate Access Token

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        // Tạo payload chứa các thông tin muốn embed vào token

        claims.put("userId", user.getUserId());   // lưu id người dùng để backend dễ lấy ra
        claims.put("email", user.getEmail());     // lưu thêm email
        if (user.getRole() != null) {
            claims.put("role", user.getRole().getRoleName()); // lưu tên role để phục vụ phân quyền nếu cần
        }

        return Jwts.builder()
                .setClaims(claims)                                      // set payload
                .setSubject(user.getEmail())                            // subject = email user
                .setIssuedAt(new Date())                               // thời điểm phát hành token
                .setExpiration(new Date(System.currentTimeMillis() + expiration)) // thời điểm hết hạn
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)   // ký token với key bí mật và thuật toán HS256
                .compact();                                            // build ra chuỗi JWT hoàn chỉnh
    }


    // ========= TẠO REFRESH TOKEN =========
//    /**
//     * Generate Refresh Token (default - 7 ngày)
//     */
//    public String generateRefreshToken(User user) {
//        // Mặc định không remember me -> dùng thời gian refreshExpiration
//        return generateRefreshToken(user, false);
//    }
//
//    /**
//     * Generate Refresh Token với Remember Me option
//     *
//     * @param user       User object
//     * @param rememberMe true = 30 ngày, false = 7 ngày
//     */
//    public String generateRefreshToken(User user, boolean rememberMe) {
//        // Nếu rememberMe = true thì dùng thời gian sống dài hơn
//        long expiry = rememberMe ? rememberMeExpiration : refreshExpiration;
//
//        return Jwts.builder()
//                .setSubject(user.getEmail())                            // chỉ cần subject = email
//                .setIssuedAt(new Date())
//                .setExpiration(new Date(System.currentTimeMillis() + expiry))
//                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
//                .compact();
//    }


    // ========= HÀM LẤY THÔNG TIN TỪ TOKEN ========= //



    // Extract email từ token (subject)

    public String extractEmail(String token) {
        // Dùng helper extractClaim với hàm lấy subject
        return extractClaim(token, Claims::getSubject);
    }

    // Extract expiration date từ token
    public Date extractExpiration(String token) {
        // Dùng helper extractClaim với hàm lấy expiration
        return extractClaim(token, Claims::getExpiration);
    }


    // ========= VALIDATE & CHECK EXPIRED =========
    // Validate token (check signature và expiration)
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())   // dùng cùng secret để verify chữ ký
                    .build()
                    .parseClaimsJws(token);          // nếu token sai chữ ký / hết hạn sẽ ném exception
            return true;                             // parse ok -> token hợp lệ
        } catch (Exception e) {
            return false;                            // có lỗi -> token không hợp lệ
        }
    }

    // ========= HELPER EXTRACT CLAIM =========
    // Extract 1 claim cụ thể từ token
    // <T> là kiểu generic, nghĩa là hàm này ko fix cứng kiểu trả về
    // tuỳ theo claimsResolver mà T có thể là String, Date, Long,...
    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        // <T> khai báo 1 "biến kiểu" T, compiler sẽ tự suy ra khi mình gọi hàm
        // vd: extractClaim(token, Claims::getSubject)     => T là String
        //        extractClaim(token, Claims::getExpiration)  => T là Date
        // Parse token và lấy ra toàn bộ phần payload (claims)
        final Claims claims = extractAllClaims(token);

        // Dùng hàm truyền vào (claimsResolver) để lấy đúng claim mình cần
        // Nhờ vậy ko phải viết nhiều hàm riêng cho từng loại claim
        return claimsResolver.apply(claims);
    }


    // Extract tất cả claims từ token
    // Dùng khi cần lấy toàn bộ payload để xử lý
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())   // dùng đúng secret key để verify và parse token
                .build()
                .parseClaimsJws(token)            // parse chuỗi JWT (header.payload.signature)
                .getBody();                       // lấy phần payload (claims)
    }


    // ========= TẠO SIGNING KEY TỪ SECRET =========
    //Get signing key từ secret
    //Chuyển chuỗi secret trong cấu hình thành SecretKey dùng cho HMAC-SHA
    private SecretKey getSigningKey() {
        // Chuyển chuỗi secret thành mảng byte UTF-8 rồi tạo SecretKey cho HMAC-SHA
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

}
