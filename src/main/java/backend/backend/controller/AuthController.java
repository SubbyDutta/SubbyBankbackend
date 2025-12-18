package backend.backend.controller;

import backend.backend.model.BankAccount;
import backend.backend.model.User;
import backend.backend.repository.PasswordResetTokenRepository;
import backend.backend.requests_response.*;
import backend.backend.service.BankService;
import backend.backend.service.UserService;
import backend.backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {


    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final BankService bankService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository tokenRepo;
    private final JavaMailSender mailSender;




    @PostMapping("/signup")
    public String signup(@RequestBody User user) {
        return userService.registerUser(user);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody AuthRequest request) {
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) throw new RuntimeException("Invalid username or password");

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new TokenResponse(token, user.getRole());
    }
    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/create-account")
    public ResponseEntity<?> createBankAccount(@Valid @RequestBody AccountRequest request) {

            User user = userService.ifUserExists(request.getUsername());

            BankAccount account = bankService.createAccount(user, request.getAdhar(), request.getPan(), request.getType());
            return ResponseEntity.ok(account);

    }



/// "REMOVED DUE TO MAIL SERVICE LIMIT REACHED"
   /* @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody  ForgotPasswordRequest request) {
        String email = request.getEmail();

         User user=userRepo.findByEmail(email).orElseThrow(()->new UsernameNotFoundException("Email Not Found"));
         if(user==null)
         {
             String otp = OtpUtil.generateOtp();

             PasswordResetToken token = new PasswordResetToken();
             token.setEmail(email);
             token.setOtp(otp);
             token.setExpiryTime(LocalDateTime.now().plusMinutes(10));
             tokenRepo.save(token);

             // generate OTP, send mail...
             SimpleMailMessage message = new SimpleMailMessage();
             message.setTo(email);
             message.setSubject("Password Reset OTP");
             message.setText("Your OTP is: " + otp);
             mailSender.send(message);


             return ResponseEntity.ok("OTP sent to email");
         }else
         {
             return ResponseEntity.ok("Email does not exists");
         }


    }
/// need change
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword( @Valid @RequestBody ResetPasswordRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();
        String newPassword = request.getNewPassword();
        // validate OTP, update password...
        PasswordResetToken token = tokenRepo.findByEmailAndOtp(email, otp)
                .orElseThrow(() -> new RuntimeException("Invalid OTP"));

        if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("OTP expired");
        }

        // update user password
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        tokenRepo.delete(token);
        return ResponseEntity.ok("Password reset successful");
    }
*/

    // DTOs
    @Data
    public static class AuthRequest {
        private String username;
        private String password;


    }

    public static class TokenResponse {
        private final String token;
        private final String role;
        public TokenResponse(String token, String role) {
            this.token = token;
            this.role = role;
        }
        public String getToken() { return token; }
        public String getRole() { return role; }
    }
}