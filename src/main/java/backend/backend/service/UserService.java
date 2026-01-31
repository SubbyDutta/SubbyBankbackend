package backend.backend.service;

import backend.backend.Dtos.UserResponseDto;
import backend.backend.Exception.ResourceNotFoundException;
import backend.backend.Exception.UnauthorizedException;
import backend.backend.model.User;
import backend.backend.repository.BankAccountRepository;
import backend.backend.repository.UserRepository;
import backend.backend.requests_response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final BankAccountRepository bankRepo;
    private final BuisnessLoggingService buisnessLoggingService;
    private final CachedLists cachedLists;



    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "banking:users:list", allEntries = true)
    })
    public void registerUser(User user) {

        if (userRepo.findByUsername(user.getUsername()).isPresent()) {
            throw new UnauthorizedException("Username already exists!");
        }

        if (userRepo.findByEmail(user.getEmail()).isPresent()) {
            throw new UnauthorizedException("Email already registered!");
        }

        if (userRepo.findByMobile(user.getMobile()).isPresent()) {
            throw new UnauthorizedException("Mobile number already exists!");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");

        if (user.getCreditScore() == 0) {
            user.setCreditScore(650);
        }
        user.setHasLoan(false);
        user.setRemaining(0);
        user.setLoanamount(0);
        user.setDueDate(null);
        userRepo.save(user);

        buisnessLoggingService.log(
                "REGISTERED",
                user.getUsername(),
                "REGISTERED WITH EMAIL " + user.getEmail() +
                        " MOBILE " + user.getMobile()
        );
    }



    public PagedResponse<UserResponseDto> getAllUsers(int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;

        List<UserResponseDto> content =
                cachedLists.getAllUserCached(page,size);

        long totalElements = userRepo.count();
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

    @Cacheable(value = "banking:user:byId", key = "#id", sync = true)
    public UserResponseDto getUserById(Long id) {
        System.out.println("DB HIT -> getUserById : " + id);
        User use = userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDto(use);
    }

    @Caching(evict = {
            @CacheEvict(value = "banking:user:byId", key = "#id"),
            @CacheEvict(value = "banking:credit:score", allEntries = true),
            @CacheEvict(value = "banking:users:list", allEntries = true)
    })
    @Transactional
    public UserResponseDto updateUser(Long id, User updated) {
        User user = userRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("User not found with ID: " + id)
        );
        System.out.println("DB HIT UPDATE USER: " + id);

        user.setEmail(updated.getEmail());
        user.setMobile(updated.getMobile());
        user.setFirstname(updated.getFirstname());
        user.setLastname(updated.getLastname());
        user.setCreditScore(updated.getCreditScore());
        user.setRole(updated.getRole());
        user.setHasLoan(updated.isHasLoan());
        user.setRemaining(updated.getRemaining());
        user.setLoanamount(updated.getLoanamount());
        User savedUser = userRepo.save(user);

        buisnessLoggingService.log("USER UPDATED", user.getUsername(),
                "UPDATED AT " + user.getUpdatedAt());

        bankRepo.findByUserId(id).ifPresent(account -> {
            account.setUser(savedUser);
            bankRepo.save(account);
        });

        return toDto(savedUser);
    }

    @Caching(evict = {
            @CacheEvict(value = "banking:user:byId", key = "#id"),
            @CacheEvict(value = "banking:credit:score", allEntries = true),
            @CacheEvict(value = "banking:users:list", allEntries = true),
            @CacheEvict(value = "banking:accounts:list", allEntries = true),
            @CacheEvict(value = "banking:user:exists", allEntries = true),
            @CacheEvict(value = "banking:account:byNumber", allEntries = true)

    })
    @Transactional
    public void deleteUser(Long id) {

        bankRepo.findByUserId(id).ifPresent(account -> {
            bankRepo.delete(account);
        });
        buisnessLoggingService.log("USER DELETED", id.toString(),
                "USER DELETED WITH ID " + id);
        userRepo.deleteById(id);
    }


    public User ifUserExists(String username) {
        System.out.println("DB HIT -> ifUserExists: " + username);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("account not found"));
        return user;
    }



    public User authenticate(String username, String password) {
        Optional<User> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) return null;

        User user = userOpt.get();
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    @Cacheable(value = "banking:credit:score", key = "#username", sync = true)
    public int fetchCreditScore(String username) {
        System.out.println("DB HIT CREDITSCORE: " + username);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        int score = user.getCreditScore();
        return score;
    }

    private UserResponseDto toDto(User u) {
        return new UserResponseDto(
                u.getId(),
                u.getUsername(),
                u.getFirstname(),
                u.getLastname(),
                u.getEmail(),
                u.getMobile(),
                u.getRole(),
                u.getCreditScore(),
                u.getUpdatedAt(),
                u.isHasLoan(),
                u.getLoanamount(),
                u.getRemaining(),
                u.getDueDate()

        );
    }
}