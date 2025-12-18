package backend.backend.service;

import backend.backend.Exception.ForbiddenException;
import backend.backend.Exception.ResourceNotFoundException;
import backend.backend.Exception.UnauthorizedException;
import backend.backend.model.BankAccount;
import backend.backend.model.IdempotencyKey;
import backend.backend.model.Transaction;
import backend.backend.model.User;
import backend.backend.repository.BankAccountRepository;
import backend.backend.repository.IdempotencyRepository;
import backend.backend.repository.TransactionRepository;
import backend.backend.repository.UserRepository;
import backend.backend.Dtos.BankAccountResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class BankService {

    private final BankAccountRepository bankRepo;
    private  final TransactionRepository txRepo;
    private final PasswordEncoder passwordEncoder;
    private final IdempotencyRepository idempotencyRepo;
    private final  TransactionService transactionService;
    private final BuisnessLoggingService buisnessLoggingService;

    @CacheEvict(value="accounts:all", allEntries=true)
    public BankAccount createAccount(User user, String adhar, String pan,String type) {

        if (bankRepo.findByUser(user).isPresent())
            throw new RuntimeException("Account already exists for this user");

        if (bankRepo.existsByPan(pan))
            throw new RuntimeException("PAN already linked to another account");

        if (bankRepo.existsByAdhar(adhar))
            throw new RuntimeException("Aadhaar already linked to another account");

        BankAccount acc = new BankAccount();
        acc.setUser(user);
        acc.setAccountNumber(UUID.randomUUID().toString().substring(0, 12));
        acc.setAdhar(adhar);
        acc.setType(type);
        acc.setPan(pan);
        acc.setBalance(0);
        acc.setVerified(true); // after OTP
       buisnessLoggingService.log("BANK ACCOUNT CREATED",acc.getAccountNumber(),"WITH PAN "+pan+"& ADHAR "+adhar);
        return bankRepo.save(acc);
    }

    @Caching(evict = {
            @CacheEvict(value="balance", allEntries=true),
            @CacheEvict(value="BankAccountResponseDto", allEntries=true),
            @CacheEvict(value = "transactions:all", allEntries = true),
            @CacheEvict(value = "transactions:user", allEntries = true)
    })
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Transaction transfer(String key, String username, Long userId, String senderAcc, String receiverAcc, double amount, String password) {

        if (idempotencyRepo.existsById(key)) {
            return null;
        }

        BankAccount sender = bankRepo.findByAccountNumber(senderAcc)
                .orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));

        User user = sender.getUser();



        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Incorrect password");
        }


        if (!user.getUsername().equals(username)) {
            throw new UnauthorizedException("Unauthorized: sender account does not belong to you");
        }


        if (sender.getBalance() < amount) {
            throw new UnauthorizedException("Insufficient balance");
        }
        if (sender.isBlocked()) {
            throw new ForbiddenException("Account is blocked");
        }
        if (amount <= 0) {
            throw new ForbiddenException("Amount must be greater than zero");
        }

        //  Deduct sender balance
        sender.setBalance(sender.getBalance() - amount);
        updateUserBalance(sender.getUser().getUsername(),sender.getBalance());// persist immediately

        //  Create transaction
        Transaction tx = new Transaction();
        tx.setUserId(userId.intValue());
        tx.setSenderAccount(senderAcc);
        tx.setAmount(amount);
        tx.setBalance(sender.getBalance());


        //  Notify sender
       /* emailService.sendEmail(user.getEmail(),
                "Debit Alert",
                "₹" + amount + " debited from account " + senderAcc + ". Balance: ₹" + sender.getBalance());*/

        //  Credit receiver if internal
        Optional<BankAccount> receiverOpt = bankRepo.findByAccountNumber(receiverAcc);
        if (receiverOpt.isPresent()) {
            BankAccount receiver = receiverOpt.get();
            receiver.setBalance(receiver.getBalance() + amount);
            updateUserBalance(receiver.getUser().getUsername(),receiver.getBalance());

            tx.setReceiverAccount(receiverAcc);
            tx.setIsForeign(0);

            /*emailService.sendEmail(receiver.getUser().getEmail(),
                    "Credit Alert",
                    "₹" + amount + " credited to account " + receiverAcc + ". Balance: ₹" + receiver.getBalance());*/
        } else {
            // External transfer
            tx.setReceiverAccount(receiverAcc);
            tx.setIsForeign(1);
        }

        //  Risk evaluation
        int risk = 0;
        boolean isSavings = sender.getType().equalsIgnoreCase("SAVINGS") ||
                (receiverOpt.isPresent() && receiverOpt.get().getType().equalsIgnoreCase("SAVINGS"));
        boolean isCurrent = sender.getType().equalsIgnoreCase("CURRENT") ||
                (receiverOpt.isPresent() && receiverOpt.get().getType().equalsIgnoreCase("CURRENT"));

        if ((isSavings && amount > 500_000) || (isCurrent && amount > 200_000)) {
            risk = 1;
        }

        if (receiverOpt.isPresent() && receiverOpt.get().getType().equalsIgnoreCase("SALARY")) {
            risk = 0;
            tx.setIs_fraud(0);
            tx.setFraud_probability(0);
        }

        //  Average transaction amount
        List<Transaction> pastTx = txRepo.findByUserId(userId.intValue());
        double total = pastTx.stream().mapToDouble(Transaction::getAmount).sum();
        double avg = pastTx.isEmpty() ? amount : (total + amount) / (pastTx.size() + 1);
        tx.setAvg_amount(avg);

        tx.setIsHighRisk(risk);

        //  Save transaction
        IdempotencyKey idempotencyKey=new IdempotencyKey();
        idempotencyKey.setKey(key);
        idempotencyKey.setCreatedAt(LocalDateTime.now());

        Transaction result= transactionService.checkFraud(tx);
        idempotencyRepo.save(idempotencyKey);
        return result;


    }

    @Caching(evict = {
            @CacheEvict(value="balance", allEntries=true),
            @CacheEvict(value="BankAccountResponseDto", allEntries=true),
            @CacheEvict(value="accounts:all", allEntries=true)
    })
   public RuntimeException deleteAccount(Long id)
   {
       if (!bankRepo.existsById(id)) {
          return new ResourceNotFoundException("Bank account not found");
       }


       buisnessLoggingService.log("DELETED BANK ACC ",getAccountByid(id).accountNumber(),"DELETED BY ADMIN");
       bankRepo.deleteById(id);
       return null;

   }
   @Cacheable(value="BankAccountResponseDto",key="#id")
   public BankAccountResponseDto getAccountByid(Long id)
   {
       System.out.println("DB HIT GETACCOUNTBYID");

       BankAccount bankAccount= bankRepo.findByUserId(id).orElseThrow(()->new ResourceNotFoundException("bank account not found"));
       return toDto(bankAccount);
   }


    @Caching(evict = {
            @CacheEvict(value="balance", allEntries = true),
            @CacheEvict(value="BankAccountResponseDto", allEntries = true),
            @CacheEvict(value="accounts:all", allEntries=true)
    })
    public void  updateUserBalance(String username,double amount)
    {
        BankAccount account = bankRepo.findByUserUsername(username)
                .orElseThrow(() ->  new ResourceNotFoundException("Bank account not found"));

        account.setBalance(amount);
        buisnessLoggingService.log("UPDATED BALANCE",account.getAccountNumber(),"UPDATED BALANCE TO "+amount);
        bankRepo.save(account);

    }
    @Caching(evict = {

            @CacheEvict(value="BankAccountResponseDto", key="#id"),
            @CacheEvict(value="accounts:all", allEntries=true)
    })
    public boolean ToggleBlock(Long id)
   {
       BankAccount acc = bankRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
       acc.setBlocked(!acc.isBlocked());
       bankRepo.save(acc);
       buisnessLoggingService.log("ACCOUNT BLOCKED",acc.getAccountNumber(),"BLOCKED BY ADMIN");
       return acc.isBlocked();

   }


   private BankAccountResponseDto toDto(BankAccount b)
   {
      return new BankAccountResponseDto( b.getId(),

              b.getAccountNumber(),
              b.getType(),
              b.getBalance(),

             b.getUser().getUsername(),
              b.isBlocked(),
              b.isVerified());

   }

}
