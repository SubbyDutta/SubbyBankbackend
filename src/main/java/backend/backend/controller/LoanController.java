package backend.backend.controller;

import backend.backend.model.LoanApplication;
import backend.backend.model.LoanEligibilityRequest;
import backend.backend.Dtos.LoanApplicationResponseDto;
import backend.backend.requests_response.PagedResponse;
import backend.backend.security.CustomUserDetails;
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
    private JwtUtil jwtutil;

    // Check eligibility
    // Check loan eligibility (calls ML service)
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/check")
    public ResponseEntity<?> checkEligibility(@RequestBody Map<String, Object> body, @RequestHeader("Authorization") String token) {
        String username = (String) body.get("username");
        String tokenUser = jwtutil.extractUsername(token.replace("Bearer ", ""));
        if (!username.equals(tokenUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        }
        LoanEligibilityRequest req = loanService.checkEligibility(
                username,
                Double.parseDouble(body.get("income").toString()),

                Double.parseDouble(body.get("requestedAmount").toString())
        );


        Map<String, Object> response = Map.of(
                "id", req.getId(),
                "eligible", req.isEligible(),
                "probability", req.getProbability(),
                "maxAmount", req.getRequestedAmount(),
                "requestedAmount", req.getRequestedAmount()
        );
        return ResponseEntity.ok(response);
    }


    //LOAN APPLY
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/apply/{eligibilityId}")
    public ResponseEntity<?> applyLoan(
            @PathVariable Long eligibilityId,
            @RequestHeader("Authorization") String token) {

        String usernameFromToken = jwtutil.extractUsername(token.replace("Bearer ", ""));
        LoanApplication loan = loanService.applyLoan(eligibilityId, usernameFromToken);
        return ResponseEntity.ok(loanService.toDto(loan));

    }




    //PENDING LOANS
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<LoanApplicationResponseDto> pendingLoans(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return loanService.getPendingLoans(page, size);
    }




    //USERS LOAN HISTORY
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/loans/myuserloan")
    public List<LoanApplicationResponseDto> myLoans(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
          return loanService.userLoans(userDetails.getUsername());
    }
}

