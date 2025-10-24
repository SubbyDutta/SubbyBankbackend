package backend.backend.service;

import backend.backend.model.BankAccount;
import backend.backend.model.Transaction;
import backend.backend.model.User;
import backend.backend.repository.BankAccountRepository;
import backend.backend.repository.TransactionRepository;
import backend.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class BankService {

   @Autowired private BankAccountRepository bankRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private final PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepo;

    public BankService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

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

        return bankRepo.save(acc);
    }

    @Transactional
    public Transaction transfer(String username, Long userId, String senderAcc, String receiverAcc, double amount, String password) {
        BankAccount sender = bankRepo.findByAccountNumber(senderAcc)
                .orElseThrow(() -> new IllegalArgumentException("Sender account not found"));

        User user = sender.getUser();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }

        if (!sender.getUser().getUsername().equals(username)) {
            throw new SecurityException("Unauthorized: sender account does not belong to you");
        }

        if (sender.getBalance() < amount) {
            throw new IllegalStateException("Insufficient balance");
        }

        if (sender.isBlocked()) {
            throw new IllegalStateException("Account is blocked");
        }
        if(amount<=0)
        {
            throw new IllegalStateException("Amount can not be zero or negative");
        }

        // Deduct from sender
        sender.setBalance(sender.getBalance() - amount);

        Transaction tx = new Transaction();
        tx.setUserId(userId.intValue());
        tx.setSenderAccount(senderAcc);
        tx.setAmount(amount);
        tx.setBalance(sender.getBalance());

        // Notify sender
        emailService.sendEmail(
                sender.getUser().getEmail(),
                "Debit Alert",
                "₹" + amount + " has been debited from your account " + senderAcc +
                        ". Available balance: ₹" + sender.getBalance()
        );

        // Try to credit receiver if internal
        Optional<BankAccount> receiverOpt = bankRepo.findByAccountNumber(receiverAcc);
        if (receiverOpt.isPresent()) {
            BankAccount receiver = receiverOpt.get();
            receiver.setBalance(receiver.getBalance() + amount);
            bankRepo.save(receiver);

            tx.setReceiverAccount(receiverAcc);
            tx.setIsForeign(0);

            // Notify receiver
            emailService.sendEmail(
                    receiver.getUser().getEmail(),
                    "Credit Alert",
                    "₹" + amount + " has been credited to your account " + receiverAcc +
                            ". Available balance: ₹" + receiver.getBalance()
            );
        } else {
            // External transfer
            tx.setReceiverAccount(receiverAcc); // or "EXTERNAL"
            tx.setIsForeign(1);
        }

        // Risk logic
        int risk = 0;
        if ((sender.getType().equals("SAVINGS") || (receiverOpt.isPresent() && receiverOpt.get().getType().equals("SAVINGS"))) && amount > 500000
                || (sender.getType().equals("CURRENT") || (receiverOpt.isPresent() && receiverOpt.get().getType().equals("CURRENT"))) && amount > 200000) {
            risk = 1;
        }

        if (receiverOpt.isPresent() && receiverOpt.get().getType().equals("SALARY")) {
            risk = 0;
            tx.setIs_fraud(0);
            tx.setFraud_probability(0);
        }

        // Average transaction amount
        List<Transaction> pastTx = txRepo.findByUserId(userId.intValue());
        double total = pastTx.stream().mapToDouble(Transaction::getAmount).sum();
        double avg = pastTx.isEmpty() ? amount : (total + amount) / (pastTx.size() + 1);
        tx.setAvg_amount(avg);

        tx.setIsHighRisk(risk);

        bankRepo.save(sender);
        return txRepo.save(tx);
    }

}
