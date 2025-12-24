package backend.backend.service;

import backend.backend.Dtos.*;
import backend.backend.model.*;
import backend.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CachedLists {
    private final BankAccountRepository bankRepo;
    private final LoanApplicationRepository applicationRepo;
    private final TransactionRepository  transactionRepository;

    private final UserRepository userRepo;
    private final LoanRepaymentRepository repaymentRepo;
    @Cacheable(
            value = "accounts:all",
            key = "'page:' + #page + ':size:' + #size"
    )
    public List<BankAccountResponseDto> getAllAccounts(int page, int size

    ) {


        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        System.out.println("DB HIT -> getallBankAccount : ");

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());


        Page<BankAccount> pageResult =
                bankRepo.findAll(pageable);


        return pageResult.getContent()
                .stream()
                .map(this::toDtoAccounts)
                .toList();
    }


    //get pending loans
    @Cacheable(
            value = "pendingapplications:all",
            key = "'page:' + #page + ':size:' + #size",
            unless = "#result == null"
    )  public List<LoanApplicationResponseDto> getUserPendingLoanCached(

            Integer page,
            Integer size


    ) {
        System.out.println("DB HIT -> getUserPendingLoans");

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        if (s > 100) s = 100;

        Pageable pageable =  PageRequest.of(page, size, Sort.by("approvedAt").descending());
        Page<LoanApplication> pageResult= applicationRepo.findByApprovedFalseAndStatusNotIgnoreCase("REJECTED", pageable);






        return pageResult.getContent()
                .stream()
                .map(this::toDtoLoanApplications)
                .toList();
    }
    @Cacheable(
            value = "loanApplications:all",
            key = "'page:' + #page + ':size:' + #size",
            unless = "#result == null"
    )
    public List<LoanApplicationResponseDto> getUserLoansCached(
            String username,
            Double minAmount,
            Integer page,
            Integer size


    ) {
        System.out.println("DB HIT -> getUserLoans");

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        if (s > 100) s = 100;

        Pageable pageable = PageRequest.of(p, s, Sort.by("timestamp").descending());




        Page<LoanApplication> pageResult =
                applicationRepo.searchLoans(username,minAmount,pageable);

        return pageResult.getContent()
                .stream()
                .map(this::toDtoLoanApplications)
                .toList();
    }
    @Cacheable(
            value = "repaylist",
            key = "'page:' + #page + ':size:' + #size",

            unless = "#result == null"
    )
    public List<LoanRepaymentResponseDto> getAllRepayList(int page, int size

    ) {


        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        System.out.println("DB HIT -> getall Loan Repayments : ");

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());


        Page<LoanRepayment> pageResult =
                repaymentRepo.findAllByOrderByPaymentDateDesc(pageable);


        return pageResult.getContent()
                .stream()
                .map(this::toDtoLoanRepayments)
                .toList();
    }

    @Cacheable(
            value = "transactions:user",
            key = "'u:' + #username + ':p:' + #page + ':s:' + #size + ':f:' + #from + ':t:' + #to + ':min:' + #minAmount + ':max:' + #maxAmount"
    )
    public List<TransactionDto> getUserTransactionsCached(
            int userId,
            Integer page,
            Integer size,
            LocalDate from,
            LocalDate to,
            Double minAmount,
            Double maxAmount
    ) {
        System.out.println("DB HIT -> getUserTransactionsFiltered");

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        if (s > 100) s = 100;

        Pageable pageable = PageRequest.of(p, s, Sort.by("timestamp").descending());

        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs = to != null ? to.plusDays(1).atStartOfDay() : null;



        Page<Transaction> pageResult =
                transactionRepository.searchByUserWithFilters(
                        userId,
                        fromTs,
                        toTs,
                        minAmount,
                        maxAmount,
                        pageable
                );

        return pageResult.getContent()
                .stream()
                .map(this::toDtoTransactions)
                .toList();
    }
    @Cacheable(
            value = "transactions:all",
            key = "'page:' + #page + ':size:' + #size",
            unless = "#result == null"
    )
    public List<TransactionDto> getAllTransactionsCached(int page,int size

    ) {


        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        System.out.println("DB HIT -> getallTransactions : ");

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());


        Page<Transaction> pageResult =
                transactionRepository.findAllByOrderByTimestampDesc(pageable);


        return pageResult.getContent()
                .stream()
                .map(this::toDtoTransactions)
                .toList();
    }
    @Cacheable(
            value = "users:all",
            key = "'page:' + #page + ':size:' + #size",
            unless = "#result == null"
    )
    public List<UserResponseDto> getAllUserCached(int page, int size

    ) {


        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        System.out.println("DB HIT -> getallUsers: ");

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());


        Page<User> pageResult =userRepo.findAll(pageable);



        return pageResult.getContent()
                .stream()
                .map(this::toDtoUser)
                .toList();
    }
    private UserResponseDto toDtoUser(User u) {
        return new UserResponseDto(
                u.getId(),
                u.getUsername(),

                u.getEmail(),

                u.getMobile(),
                u.getRole(),
                u.getCreditScore(),
                u.getUpdatedAt()




        );
    }
    private TransactionDto toDtoTransactions(Transaction t) {
        return new TransactionDto(
                t.getId(),
                t.getSenderAccount(),
                t.getReceiverAccount(),
                t.getAmount(),
                t.getFraud_probability(),
                t.getIs_fraud(),
                t.getUserId(),
                t.getIsForeign(),
                t.getHour(),
                t.getTimestamp() != null ? t.getTimestamp().toString() : null
        );
    }
    public LoanApplicationResponseDto toDtoLoanApplications(LoanApplication a) {
        return new LoanApplicationResponseDto(
                a.getId(),
                a.getUsername(),
                a.getAmount(),
                a.getDue_amount(),
                a.isApproved(),
                a.getStatus(),
                a.getMonthsRemaining(),
                a.getMonthlyEmi(),
                a.getApprovedAt(),
                a.getNextDueDate()
        );
    }
    private LoanRepaymentResponseDto toDtoLoanRepayments(LoanRepayment t) {
        return new LoanRepaymentResponseDto(
                t.getId(),
                t.getLoanId(),
                t.getUsername(),
                t.getAmountPaid(),
                t.getPaymentDate(),
                t.getRemainingBalance()

        );
    }
    private BankAccountResponseDto toDtoAccounts(BankAccount b) {
        return new BankAccountResponseDto(
                b.getId(),

                b.getAccountNumber(),
                b.getType(),
                b.getBalance(),
                b.getUser().getUsername(),
                b.isBlocked(),

                b.isVerified()
        );
    }
}
