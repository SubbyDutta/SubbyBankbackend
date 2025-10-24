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
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {
    private static final int TENURE_MONTHS = 6;
    @Autowired
    private LoanEligibilityRequestRepository eligibilityRepo;
    @Autowired
    private BankPoolService bankPoolService;

    @Autowired private LoanApplicationRepository applicationRepo;
    @Autowired private BankAccountRepository bankRepo;
    @Autowired private EmailService emailService;
    @Autowired private RestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository txRepo;
    @Autowired private LoanRepaymentRepository repaymentRepository;
    @Autowired private BankPoolRepository bankBalanceRepository;

    @Value("${loan.check.url}")
    private String loanCheckUrl;




    public LoanEligibilityRequest checkEligibility(String username, double income,  double requestedAmount) {
        LoanEligibilityRequest req = new LoanEligibilityRequest();
        req.setUsername(username);
        req.setIncome(income);
        req.setPan(bankRepo.findByUserUsername(username).orElseThrow(()->new RuntimeException("Not Found")).getPan());
        req.setAdhar(bankRepo.findByUserUsername(username).orElseThrow(()->new RuntimeException("Not Found")).getAdhar());
        req.setCreditScore(userRepository.findByUsername(username).orElseThrow(()->new RuntimeException("not found")).getCreditScore());

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
                "pan", req.getPan(),
                "adhar", req.getAdhar(),
                "credit_score", req.getCreditScore(),
                "requested_amount", requestedAmount,
                "balance", balance,
                "avg_transaction", avgAmount
        );

        Map<String, Object> response = restTemplate.postForObject(loanCheckUrl, payload, Map.class);
        req.setEligible((Boolean) response.get("eligible"));
        req.setProbability(((Number) response.get("probability")).doubleValue());
        int amount_to_pay=0;
        int rate=0;

        if(req.getCreditScore()>=750 )
        {
           rate= 10;
        }else if(req.getCreditScore()>=700)
        {
            rate=15;
        }else if(req.getCreditScore()>=650)
        {
            rate=20;
        }else if(req.getCreditScore()>=500)
        {
            rate=25;
        }

        amount_to_pay= (int) ((int) (requestedAmount*rate/100)+requestedAmount);
       req.setAmount_to_pay(amount_to_pay);
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
        loan.setDue_amount(eligibility.getAmount_to_pay());
        loan.setStatus("PENDING");
        loan.setApproved(false);
        return applicationRepo.save(loan);
    }

    @Transactional
    public LoanApplication approveLoan(Long loanId) {
        LoanApplication loan = applicationRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.isApproved() || "APPROVED".equalsIgnoreCase(loan.getStatus())) {
            throw new RuntimeException("Loan already approved");
        }

        loan.setApproved(true);
        loan.setStatus("APPROVED");
        loan.setApprovedAt(LocalDateTime.now());

        loan.setMonthlyEmi(loan.getDue_amount() / 6);
        loan.setNextDueDate(LocalDateTime.now().plusMonths(1));
        applicationRepo.save(loan);
        bankPoolService.deduct(loan.getAmount());

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
        r.setRemainingBalance(loan.getDue_amount());

        r.setPaymentDate(LocalDateTime.now());
        repaymentRepo.save(r);

        //deduct from bank
        BankPool bank=  new BankPool();



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

        if (!loan.getStatus().equals("APPROVED")) {
            throw new RuntimeException("Loan not approved yet");
        }

        User user = userRepository.findByUsername(loan.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        double remaining = loan.getDue_amount();

        List<LoanRepayment> pastPayments = repaymentRepo.findByLoanIdOrderByPaymentDateDesc(loanId);
        double totalPaid = pastPayments.stream().mapToDouble(LoanRepayment::getAmountPaid).sum();
        remaining = remaining - totalPaid;
        if(amount<loan.getAmount()/6)
        {
            throw new RuntimeException("cant pay less");
        }
        if (amount > remaining) {
            throw new RuntimeException("You are trying to pay more than owed");
        }

        BankAccount account = bankRepo.findByUserUsername(loan.getUsername())
                .orElseThrow(() -> new RuntimeException("Bank account not found"));

        if (account.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        // Deduct from balance
        account.setBalance(account.getBalance() - amount);
        bankRepo.save(account);


        LocalDateTime now = LocalDateTime.now();

        // Full repayment within first <10 days → no credit boost
        if (remaining - amount <= 0 && loan.getApprovedAt().plusDays(10).isBefore(now)) {
            user.setCreditScore(Math.min(900, user.getCreditScore() + 20));
        }
        // Regular on-time EMI
        else if (now.isBefore(loan.getNextDueDate()) || now.isEqual(loan.getNextDueDate())) {
            user.setCreditScore(Math.min(900, user.getCreditScore() + 6));
        }
        // Late payment
        else {
            user.setCreditScore(Math.max(300, user.getCreditScore() - 12));
        }
        userRepository.save(user);

        // Record repayment
        LoanRepayment repayment = new LoanRepayment();
        repayment.setLoanId(loanId);
        repayment.setUsername(loan.getUsername());
        repayment.setAmountPaid(amount);
        repayment.setPaymentDate(now);
        repayment.setRemainingBalance(remaining - amount);

        repaymentRepo.save(repayment);
        bankPoolService.add(amount);
        // Record transaction
        Transaction tx = new Transaction();
        tx.setSenderAccount(account.getAccountNumber());
        tx.setReceiverAccount("BANK");
        tx.setAmount(amount);
        tx.setBalance(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        tx.setFraud_probability(0.0);
        tx.setIs_fraud(0);
        tx.setIsHighRisk(0);
        tx.setIsForeign(0);
        tx.setUserId(account.getUser().getId().intValue());
        txRepo.save(tx);

        // Update next due date and months remaining
        if (remaining - amount <= 0) {
            loan.setStatus("PAID");
            loan.setMonthsRemaining(0);
        } else {
            loan.setMonthsRemaining(loan.getMonthsRemaining() - 1);
            loan.setNextDueDate(loan.getNextDueDate().plusMonths(1));
        }

        applicationRepo.save(loan);

        return repayment;
    }




    public LoanSummaryDTO getLoanSummary(Long loanId) {
        LoanApplication loan = applicationRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        List<LoanRepayment> payments = repaymentRepo.findByLoanIdOrderByPaymentDateDesc(loanId);
        double paid = payments.stream().mapToDouble(LoanRepayment::getAmountPaid).sum();
        double remaining = loan.getDue_amount() - paid;


        return new LoanSummaryDTO(
                loan.getId(),
                loan.getAmount(),
                remaining,
                loan.getMonthlyEmi(),
                loan.getNextDueDate(),
                loan.getMonthsRemaining()


        );
    }



    private double totalPaid(Long loanId) {
        return repaymentRepo.findByLoanIdOrderByPaymentDateDesc(loanId)
                .stream()
                .mapToDouble(LoanRepayment::getAmountPaid)
                .sum();
    }

    private int monthsBetween(LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start)) return 0;
        int months = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue());
        // if day-of-month in end is before approval’s day, treat as not completed the current month
        if (end.getDayOfMonth() < start.getDayOfMonth()) months = Math.max(months - 1, 0);
        return months;
    }

    private LocalDateTime dueDateForInstallment(LocalDateTime approvedAt, int installmentNo) {
        // installmentNo is 1..TENURE_MONTHS
        LocalDateTime base = approvedAt.plusMonths(installmentNo);
        // Keep day-of-month semantics: if month shorter, use month-end
        YearMonth ym = YearMonth.from(base);
        int dom = Math.min(approvedAt.getDayOfMonth(), ym.lengthOfMonth());
        return LocalDateTime.of(ym.getYear(), ym.getMonth(), dom, approvedAt.getHour(), approvedAt.getMinute(), approvedAt.getSecond());
    }

    private int clampCredit(int v) {
        if (v < 300) return 300;
        if (v > 900) return 900;
        return v;
    }




}

