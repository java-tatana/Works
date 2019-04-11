package backend.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
public class CompanyServiceImpl implements CompanyService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private CompanyRepository companyRepository;

    @Override
    public Mono<Company> getCurrentUserCompany() {
        return accountService.getCurrentUserAccount()
                .flatMap(account -> companyRepository.findById(account.getCompanyId()));
    }

    @Override
    public Mono<Company> createOrUpdateCurrentCompany(Company newCompany) {
        newCompany.setId(null);
        return getCurrentUserCompany()
                .flatMap(company -> {
                    company.setTitle(newCompany.getTitle());
                    company.setWebsiteUrl(newCompany.getWebsiteUrl());
                    company.setSupportUrl(newCompany.getSupportUrl());
                    company.setNotifyFromEmail(newCompany.getNotifyFromEmail());
                    return companyRepository.save(company);
                })
                .switchIfEmpty(companyRepository.save(newCompany));
    }
}

