package backend.backend.service;

import backend.backend.Dtos.BankAccountResponseDto;
import backend.backend.Exception.BadRequestException;
import backend.backend.configuration.RazorpayProperties;
import backend.backend.model.IdempotencyKey;
import backend.backend.model.Transaction;
import backend.backend.model.User;
import backend.backend.repository.IdempotencyRepository;
import backend.backend.repository.UserRepository;
import backend.backend.requests_response.PaymentVerifyRequest;
import com.razorpay.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class RazorpayService {


    private final RazorpayProperties razorpayProperties;

    private final UserService userService;
    private final TransactionService transactionService;
    private final BankService bankService;
    private final IdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;


    String keyId ;
    String keySecret ;
    @PostConstruct
    public void init() {
       this.keyId=razorpayProperties.getKeyId();
       this.keySecret=razorpayProperties.getSecret();
    }

    public Order createOrder(int amountInRupees, String receiptId) throws RazorpayException {
        RazorpayClient client = new RazorpayClient(keyId, keySecret);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInRupees * 100); // amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receiptId);
        orderRequest.put("payment_capture", 1);

        return client.orders.create(orderRequest);
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            return Utils.verifySignature(payload, signature, keySecret);
        } catch (Exception e) {
            return false;
        }
    }
    @Caching(evict = {

            @CacheEvict(value="BankAccountResponseDto", allEntries=true),
            @CacheEvict(value = "transactions:all", allEntries = true),
            @CacheEvict(value = "transactions:user", allEntries = true)
    })
    @Transactional
    public void verifyAndCredit(PaymentVerifyRequest req,String key) {
        if (!idempotencyRepository.existsById(key)) {
            double amount = parseAmount(req.amount());


            boolean verified = verifySignature(
                    req.razorpay_order_id(),
                    req.razorpay_payment_id(),
                    req.razorpay_signature()
            );

            if (!verified) {
                throw new BadRequestException("Invalid Razorpay signature");
            }


            User user = userService.ifUserExists(req.username());
            BankAccountResponseDto account =
                    bankService.getAccountByid(user.getId());


            bankService.updateUserBalance(
                    req.username(),
                    account.balance() + amount
            );


            Transaction txn = buildTransaction(user, account, amount);


            transactionService.checkFraud(txn);
            IdempotencyKey idempotencyKey=new IdempotencyKey();
            idempotencyKey.setKey(key);
            idempotencyKey.setCreatedAt(LocalDateTime.now());
            idempotencyRepository.save(idempotencyKey);
        }

    }

    private double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank())
            throw new BadRequestException("Amount missing");

        double amount = Double.parseDouble(amountStr);
        if (amount <= 0)
            throw new BadRequestException("Invalid amount");

        return amount;
    }

    private Transaction buildTransaction(
            User user,
            BankAccountResponseDto account,
            double amount
    ) {
        Transaction txn = new Transaction();
        txn.setSenderAccount("RAZORPAY_TOPUP");
        txn.setReceiverAccount(account.accountNumber());
        txn.setAmount(amount);
        txn.setBalance(account.balance());
        txn.setIsForeign(0);
        txn.setIsHighRisk(0);
        txn.setFraud_probability(0);
        txn.setIs_fraud(0);
        txn.setUserId(user.getId().intValue());
        return txn;
    }

    public Map<String, Object> createOrder(Map<String, Object> data) {
        try {
            int amount = (int) data.get("amount");
            String receipt = "txn_" + System.currentTimeMillis();

            Order order = createOrder(amount, receipt);

            JSONObject response = new JSONObject();
            response.put("orderId", order.get("id").toString());
            response.put("amount", order.get("amount").toString());
            response.put("currency", order.get("currency").toString());
            response.put("key", "rzp_test_RVPxvjAjcRvhXL");

            return response.toMap();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }
}

