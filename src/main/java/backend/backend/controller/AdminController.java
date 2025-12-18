package backend.backend.controller;

import backend.backend.Dtos.BankAccountResponseDto;
import backend.backend.Dtos.UserResponseDto;
import backend.backend.model.User;
import backend.backend.requests_response.*;
import backend.backend.service.AccountService;
import backend.backend.service.BankService;
import backend.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {


    private final  UserService userService;
    private final AccountService accountService;
    private final BankService bankService;

   //GET ALL USERS
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<UserResponseDto> getAllUsers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return userService.getAllUsers(page,size);
    }


   //SEARCH USER BY ID
    @GetMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDto getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }


  //UPDATE USER
    @PutMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDto updateUser(@PathVariable Long id, @RequestBody User updated) {
        return userService.updateUser(id, updated);
    }

//DELETE USER
    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteUser(@PathVariable Long id) {

        userService.deleteUser(id);

        return "User deleted";
    }

//TOGGLE BLOCK
    /// need chagne
    @PatchMapping("/block/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggleBlock(@PathVariable Long id) {
          boolean acc=  bankService.ToggleBlock(id);
        return acc? "Account blocked" : "Account unblocked";
    }

//GET BALANCE
    @GetMapping("/balance/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public double getBalance(@PathVariable Long id) {

        return bankService.getAccountByid(id).balance();
    }


//GET ALL BANK ACCOUNTS
    @GetMapping("/bankaccounts")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<BankAccountResponseDto> getAllAccountss(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "1") Integer size
    ) {
        return accountService.getAllAccountsPaged(page,size);
    }
    //UPDATE USER

    @PatchMapping("/balance/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserBalance(@RequestBody BalanceUpdateRequest request) {
        bankService.updateUserBalance(userService.getUserById(request.getUserId()).username(), request.getAmount());

        return ResponseEntity.ok("Balance updated to ₹" + request.getAmount());
    }
//GET BANK ACCOUNT BY ID

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BankAccountResponseDto getAccountById(@PathVariable Long id) {
        return bankService.getAccountByid(id);

    }

//DELETE BANK ACCOUTN BY ID
    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAccountById(@PathVariable Long id) {
      bankService.deleteAccount(id);
        return ResponseEntity.ok("Bank account deleted successfully with ID: " + id);
    }


}
