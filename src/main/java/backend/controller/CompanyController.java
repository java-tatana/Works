package backend.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/api/company")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @GetMapping
    public Mono<ResponseEntity<Company>> getCurrentUserCompany() {
        return companyService.getCurrentUserCompany()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Mono<ResponseEntity<Company>> updateCurrentUserCompany(@RequestBody Company company) {
        return companyService.getCurrentUserCompany()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "The current user has no company, please finish quick setup first")))
                .then(companyService.createOrUpdateCurrentCompany(company))
                .map(ResponseEntity::ok);
    }
}