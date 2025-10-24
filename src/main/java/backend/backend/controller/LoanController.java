package backend.backend.controller;

import backend.backend.model.LoanApplication;
import backend.backend.model.LoanEligibilityRequest;
import backend.backend.model.LoanRepayment;
import backend.backend.repository.LoanApplicationRepository;
import backend.backend.repository.LoanEligibilityRequestRepository;
import backend.backend.repository.UserRepository;
import backend.backend.security.JwtUtil;
import backend.backend.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loan")
public class LoanController {

    @Autowired
    private LoanService loanService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private JwtUtil jwtutil;
    @Autowired
    private  LoanEligibilityRequestRepository loanEligibilityRequestRepository;



    // Check eligibility
    // Check loan eligibility (calls ML service)
    @PostMapping("/check")
    public ResponseEntity<?> checkEligibility(@RequestBody Map<String, Object> body, @RequestHeader("Authorization") String token) {
        String username = (String) body.get("username");

        // Optional: Verify that JWT user matches body username
        // Extract username from JWT token
        String tokenUser = jwtutil.extractUsername(token.replace("Bearer ", ""));
        if (!username.equals(tokenUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        }

        LoanEligibilityRequest req = loanService.checkEligibility(
                username,
                Double.parseDouble(body.get("income").toString()),
                (String) body.get("pan"),
                (String) body.get("adhar"),
                Double.parseDouble(body.get("creditScore").toString()),
                Double.parseDouble(body.get("requestedAmount").toString())
        );

        // Return max allowed loan as well
        Map<String, Object> response = Map.of(
                "id", req.getId(),
                "eligible", req.isEligible(),
                "probability", req.getProbability(),
                "maxAmount", req.getRequestedAmount(), // this should be set by ML or rule in service
                "requestedAmount", req.getRequestedAmount()
        );
        return ResponseEntity.ok(response);
    }

    // Apply for loan
    @PostMapping("/apply/{eligibilityId}")
    public ResponseEntity<?> applyLoan(
            @PathVariable Long eligibilityId,
            @RequestHeader("Authorization") String token) {

        String usernameFromToken = jwtutil.extractUsername(token.replace("Bearer ", ""));
        try {
            LoanApplication loan = loanService.applyLoan(eligibilityId, usernameFromToken);
            return ResponseEntity.ok(loan);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    // Admin approve
    @PostMapping("/approve/{loanId}")
    @PreAuthorize("hasRole('ADMIN')")
    public LoanApplication approveLoan(@PathVariable Long loanId) {
        return loanService.approveLoan(loanId);
    }

    //  Admin pending loans
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LoanApplication> pendingLoans() {
        return loanService.getPendingLoans();
    }

    @GetMapping("/user/loan-status")
    public LoanApplication getUserLoanStatus(@AuthenticationPrincipal org.springframework.security.core.userdetails.User userDetails) {
        return loanApplicationRepository.findByUsername(userDetails.getUsername())
                .stream()
                .filter(loan -> {
                    String status = loan.getStatus().toUpperCase();
                    return status.equals("PENDING") || status.equals("APPROVED");
                })
                .findFirst()
                .orElse(null);
    }

}
