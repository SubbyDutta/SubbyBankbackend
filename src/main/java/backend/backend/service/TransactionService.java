package backend.backend.service;

import backend.backend.configuration.FraudMlProperties;
import backend.backend.model.Transaction;
import backend.backend.model.User;
import backend.backend.repository.IdempotencyRepository;
import backend.backend.repository.TransactionRepository;
import backend.backend.requests_response.PagedResponse;
import backend.backend.Dtos.TransactionDto;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TransactionService {

    private final FraudMlProperties fraudMlProperties;
    private final UserService userService;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;
   private final BuisnessLoggingService buisnessLoggingService;
    private final CachedLists cachedLists;
    private String mlUrl;

    @PostConstruct
    public void init() {
        this.mlUrl = fraudMlProperties.geturl();
    }

    @Caching(evict = {
            @CacheEvict(value = "transactions:all", allEntries = true),
            @CacheEvict(value = "transactions:user", allEntries = true)
    })
    public Transaction saveTransaction(Transaction transaction) {

        return transactionRepository.save(transaction);
    }
    @Transactional
    public Transaction checkFraud(Transaction transaction) {
        try{
        Map<String, Object> body = new HashMap<>();
        body.put("transactions", Collections.singletonList(transaction));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(mlUrl, entity, Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
        Map<String, Object> res = results.get(0);

        if ("BANK".equals(transaction.getSenderAccount()) ||
                "RAZORPAY_TOPUP".equals(transaction.getSenderAccount())) {
            transaction.setFraud_probability(0.0);
            transaction.setIs_fraud(0);
        } else {
            transaction.setFraud_probability((Double) res.get("fraud_probability"));
            transaction.setIs_fraud((Integer) res.get("is_fraud"));
        }
        buisnessLoggingService.log("TRANSFER",transaction.getSenderAccount(),"FROM "+transaction.getSenderAccount()+"TO "+transaction.getReceiverAccount()+" OF AMOUNT "+transaction.getAmount());
        saveTransaction(transaction);
        return transaction;
    } catch (Exception e) {
            transaction.setIs_fraud(0);
            transaction.setFraud_probability(0.00);
            saveTransaction(transaction);
            return  transaction;

        }}


    public PagedResponse<TransactionDto> getAllTransactionsPaged(int page, int size) {
        List<TransactionDto> content =
                cachedLists.getAllTransactionsCached(page,size);

        long totalElements = transactionRepository.count();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PagedResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 >= totalPages
        );



    }

    public PagedResponse<TransactionDto> getUserTransactionsFiltered(String username,
                                                                     Integer page,
                                                                     Integer size,
                                                                     LocalDate from,
                                                                     LocalDate to,
                                                                     Double minAmount,
                                                                     Double maxAmount) {
        User user = userService.ifUserExists(username);
        List<TransactionDto> content =
                cachedLists.getUserTransactionsCached(user.getId().intValue(), page, size, from, to, minAmount, maxAmount);

        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to != null ? to.plusDays(1).atStartOfDay() : null;
        long totalElements =
                transactionRepository.countByUserWithFilters(
                        user.getId().intValue(),
                        fromTs,
                        toTs,
                        minAmount,
                        maxAmount
                );
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PagedResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 >= totalPages
        );
    }


    private TransactionDto toDto(Transaction t) {
        return new TransactionDto(
                t.getId(),
                t.getSenderAccount(),
                t.getReceiverAccount(),
                t.getAmount(),
                t.getFraud_probability(),
                t.getIs_fraud(),
                t.getUserId(),
                t.getTimestamp() != null ? t.getTimestamp().toString() : null
        );
    }
}
