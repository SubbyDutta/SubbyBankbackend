package backend.backend.service;

import backend.backend.Dtos.TransactionDto;
import backend.backend.Dtos.UserResponseDto;
import backend.backend.Exception.ResourceNotFoundException;
import backend.backend.model.Transaction;
import backend.backend.model.User;
import backend.backend.repository.BankAccountRepository;
import backend.backend.repository.TransactionRepository;
import backend.backend.repository.UserRepository;
import backend.backend.requests_response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final BankAccountRepository bankRepo;
    private final BuisnessLoggingService buisnessLoggingService;
    private final CachedLists cachedLists;

    // Register user
    @Caching(evict =
            {
                    @CacheEvict(value="users:all" ,allEntries=true)
            }
    )
    public String registerUser(User user) {
        Optional<User> existing = userRepo.findByUsername(user.getUsername());
        if (existing.isPresent()) {
            return "Username already exists!";
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");
        if(user.getCreditScore()==0)
        {
            user.setCreditScore(650);
        }

        userRepo.save(user);
      buisnessLoggingService.log("REGISTERED",user.getUsername(),"REGISTERED WITH EMAIL "+user.getEmail()+" MOBILE "+user.getMobile());
        return "Signup successful!";
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
    @Cacheable(value = "user", key = "#id")
    public UserResponseDto getUserById(Long id) {
        System.out.println("DB HIT -> getUserById : ");
        User use=userRepo.findById(id).orElseThrow(()->new RuntimeException());
           return toDto(use);

    }

@Caching(evict={
        @CacheEvict(value = "user", key = "#id",beforeInvocation = true),
        @CacheEvict(value = "score", allEntries = true),
        @CacheEvict(value="users:all" ,allEntries=true)
})
    public UserResponseDto updateUser(Long id, User updated) {
        User user = userRepo.findById(id).orElseThrow(() ->
                new RuntimeException("User not found with ID: " + id)
        );
    System.out.println("DB HIT UPDSTE USEER");
        user.setEmail(updated.getEmail());
        user.setMobile(updated.getMobile());
        user.setRole(updated.getRole());
        User savedUser = userRepo.save(user);

      buisnessLoggingService.log("USER UPDATED",user.getUsername(),"UPDATED AT "+user.getUpdatedAt());
        bankRepo.findByUserId(id).ifPresent(account -> {
            account.setUser(savedUser);
            bankRepo.save(account);
        });

        return toDto(savedUser);
    }

    @Caching(evict={
            @CacheEvict(value = "user", key = "#id",beforeInvocation = true),
            @CacheEvict(value = "score", allEntries = true),
            @CacheEvict(value="users:all" ,allEntries=true),
            @CacheEvict(value="existsuser",allEntries = true)
    })
    public void deleteUser(Long id) {

        bankRepo.findByUserId(id).ifPresent(account -> {
            bankRepo.delete(account);
        });
        buisnessLoggingService.log("USER DELETED",id.toString(),"USER DELETED WITH ID"+id);
        userRepo.deleteById(id);
    }

  public User ifUserExists(String username)
  {

      User user=userRepo.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("account not found"));
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

    @Cacheable(value = "score", key = "#username")
    public int fetchCreditScore(String username)
    {
        System.out.println("DB HIT CREDITSCORE");
        User user= userRepo.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("user not found"));
        int score=user.getCreditScore();
        return score;
    }

    private UserResponseDto toDto(User u) {
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



}