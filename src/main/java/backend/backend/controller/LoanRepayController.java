package backend.backend.controller;

import backend.backend.model.LoanApplication;
import backend.backend.model.LoanRepayment;
import backend.backend.model.LoanSummaryDTO;
import backend.backend.repository.LoanApplicationRepository;
import backend.backend.repository.LoanRepaymentRepository;
import backend.backend.repository.UserRepository;
import backend.backend.Dtos.LoanRepaymentResponseDto;
import backend.backend.requests_response.PagedResponse;
import backend.backend.security.CustomUserDetails;
import backend.backend.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repay")
public class LoanRepayController {
    @Autowired
    private LoanService loanService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    LoanRepaymentRepository loanRepaymentRepository;

//USER REPAY
    @PostMapping("/repay/{loanId}")
    public LoanRepayment repayLoan(@PathVariable Long loanId,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String, Object> body) {
        double amount = Double.parseDouble(body.get("amount").toString());
        System.out.println("OKK");
        return loanService.repayLoan(loanId, amount,key);
    }
    //ADMIN GET ALL REPAYMENTS
    @GetMapping
    public PagedResponse<LoanRepaymentResponseDto> getAllRepayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return loanService.repayList(page,size);
    }
//FOR USER
    @GetMapping("/repayments")
    @PreAuthorize("hasRole('USER')")
    public List<LoanRepayment> getUserRepayments(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return loanRepaymentRepository.findByUsernameOrderByPaymentDateDesc(userDetails.getUsername());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/repayments")
    public ResponseEntity<List<LoanRepayment>> getAllRepayments(
            @RequestParam(required = false) String username) {

        List<LoanRepayment> repayments;

        if (username != null && !username.isBlank()) {
            repayments = loanRepaymentRepository.findByUsernameOrderByPaymentDateDesc(username);
        } else {
            repayments = loanRepaymentRepository.findAll()
                    .stream()
                    .sorted((a, b) -> b.getPaymentDate().compareTo(a.getPaymentDate()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(repayments);
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/summary/{loanId}")
    public LoanSummaryDTO getSummary(@PathVariable Long loanId) {
        return loanService.getLoanSummary(loanId);
    }

    @GetMapping("/user/approved")
    @PreAuthorize("hasRole('USER')")
    public List<LoanApplication> getUserApprovedLoans(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String username = userDetails.getUsername();
        return loanService.getUserApprovedLoans(username,"APPROVED");
    }

}
