package backend.backend.service;
//avid
import backend.backend.model.Transaction;
import backend.backend.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {
    @Value("${fraud.ml.url}")
    private String mlUrl;


    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        this.restTemplate = new RestTemplate();
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    // Send to ML model and get fraud prediction
    public List<Transaction> checkFraud(List<Transaction> transactions) {


        Map<String, Object> body = new HashMap<>();
        body.put("transactions", transactions);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(mlUrl, entity, Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");

        List<Transaction> processed = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            Map<String, Object> res = results.get(i);
            tx.setFraud_probability((Double) res.get("fraud_probability"));
            tx.setIs_fraud((Integer) res.get("is_fraud"));

            if(tx.getSenderAccount().equals("BANK")||tx.getSenderAccount().equals("RAZORPAY_TOPUP")) {
                tx.setFraud_probability(0);
                tx.setIs_fraud(0);
            }else{
                tx.setFraud_probability((Double) res.get("fraud_probability"));
                tx.setIs_fraud((Integer) res.get("is_fraud"));
            }
            saveTransaction(tx);
            processed.add(tx);
        }
        return processed;
    }


    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllTransactions().stream()
                .sorted((a,b)-> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }
}