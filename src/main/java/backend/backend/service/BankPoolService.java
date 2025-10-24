package backend.backend.service;

import backend.backend.model.BankPool;
import backend.backend.repository.BankPoolRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class BankPoolService {

    private final BankPoolRepository poolRepo;

    public BankPoolService(BankPoolRepository poolRepo) {
        this.poolRepo = poolRepo;
    }

    @PostConstruct
    public void initPool() {
        if (poolRepo.count() == 0) {
            BankPool p = new BankPool();
            p.setBalance(1000000); // initial bank reserve (set whatever you want)
            poolRepo.save(p);
        }
    }

    public BankPool getPool() {
        return poolRepo.findAll().get(0);
    }

    @Transactional
    public void deduct(double amount) {
        BankPool p = getPool();
        if (p.getBalance() < amount) {
            throw new RuntimeException("Bank does not have enough funds to issue this loan.");
        }
        p.setBalance(p.getBalance() - amount);
        poolRepo.save(p);
    }

    @Transactional
    public void add(double amount) {
        BankPool p = getPool();
        p.setBalance(p.getBalance() + amount);
        poolRepo.save(p);
    }
}
