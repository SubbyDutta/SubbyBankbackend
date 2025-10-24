package backend.backend.service;

import backend.backend.model.*;
import backend.backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    @Autowired
    private LoanEligibilityRequestRepository eligibilityRepo;
    @Autowired private LoanApplicationRepository applicationRepo;
    @Autowired private BankAccountRepository bankRepo;
    @Autowired private EmailService emailService;
    @Autowired private RestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository txRepo;
    @Autowired private LoanRepaymentRepository repaymentRepository;

    @Value("${loan.check.url}")
    private String loanCheckUrl;



    // Check loan eligibility using ML
    public LoanEligibilityRequest checkEligibility(String username, double income, String pan, String adhar, double creditScore, double requestedAmount) {
        LoanEligibilityRequest req = new LoanEligibilityRequest();
        req.setUsername(username);
        req.setIncome(income);
        req.setPan(pan);
        req.setAdhar(adhar);
        req.setCreditScore(creditScore);
        req.setRequestedAmount(requestedAmount);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        BankAccount bank = bankRepo.findByUserUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Bank account not found"));

        double balance = bank.getBalance();
        double avgAmount = txRepo.findByUserId(user.getId().intValue())
                .stream()
                .mapToDouble(Transaction::getAmount)
                .average()
                .orElse(0.0);

        req.setBalance(balance);
        req.setAvg_transaction(avgAmount);

        Map<String, Object> payload = Map.of(
                "income", income,
                "pan", pan,
                "adhar", adhar,
                "credit_score", creditScore,
                "requested_amount", requestedAmount,
                "balance", balance,
                "avg_transaction", avgAmount
        );

        Map<String, Object> response = restTemplate.postForObject(loanCheckUrl, payload, Map.class);
        req.setEligible((Boolean) response.get("eligible"));
        req.setProbability(((Number) response.get("probability")).doubleValue());

        return eligibilityRepo.save(req);
    }

    // User applies for loan
    public LoanApplication applyLoan(Long eligibilityId, String usernameFromToken) {

        LoanEligibilityRequest eligibility = eligibilityRepo.findById(eligibilityId)
                .orElseThrow(() -> new RuntimeException("Eligibility not found"));

        // Ensure JWT username matches eligibility record
        if (!eligibility.getUsername().equals(usernameFromToken)) {
            throw new RuntimeException("Unauthorized to apply for this loan");
        }

        if (!eligibility.isEligible()) {
            throw new RuntimeException("User not eligible for this loan");
        }

        // Check if requested amount exceeds max allowed
        if (eligibility.getRequestedAmount() > eligibility.getMaxamoount()) {
            throw new RuntimeException("Requested amount exceeds maximum allowed based on eligibility");
        }

        // Block if user has active loan (PENDING or APPROVED)
        boolean hasActiveLoan = applicationRepo.findByUsername(eligibility.getUsername())
                .stream()
                .anyMatch(loan -> {
                    String status = loan.getStatus().toUpperCase();
                    return status.equals("PENDING") || status.equals("APPROVED");
                });

        if (hasActiveLoan) {
            throw new RuntimeException("You already have an active loan. Repay it before applying again.");
        }

        LoanApplication loan = new LoanApplication();
        loan.setUsername(eligibility.getUsername());
        loan.setAmount(eligibility.getRequestedAmount());
        loan.setStatus("PENDING");
        loan.setApproved(false);
        return applicationRepo.save(loan);
    }
    // dmin approves loan and credits user's bank account
    @Transactional
    public LoanApplication approveLoan(Long loanId) {
        LoanApplication loan = applicationRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.isApproved() || "APPROVED".equalsIgnoreCase(loan.getStatus())) {
            throw new RuntimeException("Loan already approved");
        }

        loan.setApproved(true);
        loan.setStatus("APPROVED");
        applicationRepo.save(loan);

        // Credit money to user account
        BankAccount account = bankRepo.findByUserUsername(loan.getUsername())
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
        account.setBalance(account.getBalance() + loan.getAmount());
        bankRepo.save(account);

        // Record transaction
        Transaction tx = new Transaction();
        tx.setSenderAccount("BANK");
        tx.setReceiverAccount(account.getAccountNumber());
        tx.setAmount(loan.getAmount());
        tx.setBalance(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        tx.setFraud_probability(0);
        tx.setIs_fraud(0);
        tx.setIsHighRisk(0);
        tx.setIsForeign(0);
        txRepo.save(tx);

        // Create repayment record
        LoanRepayment r=new LoanRepayment();
        r.setLoanId(loanId);
        r.setUsername(loan.getUsername());
        r.setAmountPaid(0);
        r.setRemainingBalance(loan.getAmount());

        r.setPaymentDate(LocalDateTime.now());
        repaymentRepo.save(r);


        return loan;
    }

    // Get all pending loans for admin
    public List<LoanApplication> getPendingLoans() {
        return applicationRepo.findAll()
                .stream()
                .filter(a -> !a.isApproved() && !"REJECTED".equalsIgnoreCase(a.getStatus()))
                .toList();
    }



    @Autowired
    private LoanRepaymentRepository repaymentRepo;

    @Transactional
    public LoanRepayment repayLoan(Long loanId, double amount) {
        LoanApplication loan = applicationRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.isApproved()) {
            throw new RuntimeException("Loan not approved yet");
        }
        if(amount<=0)
        {
            throw new IllegalStateException("amount can not be zero or negative");
        }

        double totalPaid = repaymentRepo.findByLoanIdOrderByPaymentDateDesc(loanId)
                .stream()
                .mapToDouble(LoanRepayment::getAmountPaid)
                .sum();

        double remaining = loan.getAmount() - totalPaid;



        if (amount > remaining) {
            throw new RuntimeException("Repayment exceeds remaining balance. You can only pay ₹" + remaining);
        }

        BankAccount account = bankRepo.findByUserUsername(loan.getUsername())
                .orElseThrow(() -> new RuntimeException("Bank account not found"));

        if (account.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        account.setBalance(account.getBalance() - amount);
        bankRepo.save(account);

        LoanRepayment repayment = new LoanRepayment();
        repayment.setLoanId(loanId);
        repayment.setUsername(loan.getUsername());
        repayment.setAmountPaid(amount+totalPaid);
        repayment.setPaymentDate(LocalDateTime.now());
        repayment.setRemainingBalance(remaining - amount);
        if (remaining - amount <= 0) {
            loan.setStatus("PAID");
            applicationRepo.save(loan);

            // 🧹 Delete all repayment records for this loan
            repaymentRepo.deleteByLoanId(loanId);
        } else {
            repaymentRepo.save(repayment);
        }
        return  repayment;
    }



}

