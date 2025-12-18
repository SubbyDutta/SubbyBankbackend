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
    private final IdempotencyRepository idempotencyRepo;
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

    @Cacheable(
            value = "transactions:all",
            key = "'page:' + #page + ':size:' + #size",
            unless = "#result == null"
    )
    public PagedResponse<TransactionDto> getAllTransactionsPaged(int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        System.out.println("DB HIT -> getallTransactionsFiltered : ");

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Transaction> pageResult = transactionRepository.findAllByOrderByTimestampDesc(pageable);

        List<TransactionDto> content = pageResult.getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
    }

    @Cacheable(
            value = "transactions:user",
            key = "'u:' + #username + ':p:' + #page + ':s:' + #size + ':f:' + #from + ':t:' + #to + ':min:' + #minAmount + ':max:' + #maxAmount",
            unless = "#result == null"
    )
    public PagedResponse<TransactionDto> getUserTransactionsFiltered(
            String username,
            Integer page,
            Integer size,
            LocalDate from,
            LocalDate to,
            Double minAmount,
            Double maxAmount
    ) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        if (s > 100) s = 100;
        System.out.println("DB HIT -> getUserTransactionsFiltered : ");
        Pageable pageable = PageRequest.of(p, s, Sort.by("timestamp").descending());

        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs = to != null ? to.plusDays(1).atStartOfDay() : null;

        User user = userService.ifUserExists(username);

        Page<Transaction> pageResult = transactionRepository.searchByUserWithFilters(
                user.getId().intValue(),
                fromTs,
                toTs,
                minAmount,
                maxAmount,
                pageable
        );

        List<TransactionDto> content = pageResult.getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
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
