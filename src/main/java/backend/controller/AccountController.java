package backend.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/api/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public Mono<ResponseEntity<Account>> getCurrentUserAccount() {
        return accountService.getCurrentUserAccount()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Mono<ResponseEntity<Account>> updateCurrentUserAccount(@RequestBody Account account) {
        return accountService.updateCurrentAccountInfo(account)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("{id}")
    public Mono<ResponseEntity<Void>> deleteAccountById(@PathVariable("id") String id) {
        return accountService.deleteMemberAccount(id)
                .map(isDeleted -> {
                    if (isDeleted) {
                        return new ResponseEntity<>(HttpStatus.OK);
                    } else {
                        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    }
                });
    }
}
