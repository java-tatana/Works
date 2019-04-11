package backend.service;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FixtureServiceImpl implements FixtureService {

    @Autowired
    private Logger logger;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private MetricsStatsRepository metricsStatsRepository;

    @Value("${enable.fixture.bootstrap:false}")
    private Boolean bootstrapEnabled;

    private static final int METRICS_PER_JOB_AMOUNT = 200;

    @PostConstruct
    @Override
    public void bootstrapFixtures() {
        if (!bootstrapEnabled) {
            logger.info("Bootstrapping disabled, skipping fixture creation...");
            return;
        }

        logger.info("Starting fixture bootstrapping...");
        getOrCreateFixtureCompany()
                .flatMap(this::getOrCreateFixtureAccountForCompany)
                .map(Account::getCompanyId)
                .flatMap(this::getOrCreateFixtureProjectForCompany)
                .then(Mono.fromRunnable(() -> logger.info("Fixture bootstrapping completed")))
                .doOnError(throwable -> logger.error("Error creating fixtures: ", throwable))
                .subscribe();
    }

    private Mono<Company> getOrCreateFixtureCompany() {
        return companyRepository.findById(FixtureIds.COMPANY.getId())
                .doOnNext(company -> logger.info("Fixture company already exists, skipping test company creation..."))
                .switchIfEmpty(Mono.defer(() -> {
                    Company company = new Company();
                    company.setId(FixtureIds.COMPANY.getId());
                    company.setTitle("Fixture company");
                    company.setWebsiteUrl("FixtureUrl.com");
                    company.setSupportUrl("FixtureSupportUrl.com");
                    company.setNotifyFromEmail("fixture@email.com");
                    return companyRepository.save(company)
                            .doOnNext(c -> logger.info("Fixture company successfully created"));
                }));
    }

    private Mono<Account> getOrCreateFixtureAccountForCompany(Company company) {
        return accountRepository.findById(FixtureIds.ACCOUNT.getId())
                .doOnNext(account -> logger.info("Fixture account already exists, skipping test account creation..."))
                .switchIfEmpty(Mono.defer(() -> {
                    Account account = new Account();
                    account.setId(FixtureIds.ACCOUNT.getId());
                    account.setUserId(FixtureIds.USER.getId());
                    account.setCompanyId(company.getId());
                    account.setFirstName("John");
                    account.setLastName("Doe");
                    account.setBirthday(LocalDate.of(1995, 8, 30));
                    account.setStreet1("221B Baker str.");
                    account.setCity("London");
                    account.setCountry("UK");
                    account.setTaxId("12345");
                    account.setZIP("E1");
                    return accountRepository.save(account)
                            .doOnNext(a -> logger.info("Fixture account successfully created"));
                }));
    }

    private Mono<Project> getOrCreateFixtureProjectForCompany(String companyId) {
        return projectRepository.findById(FixtureIds.PROJECT.getId())
                .doOnNext(project -> logger.info("Fixture project already exists, skipping test project creation..."))
                .switchIfEmpty(Mono.defer(() -> {
                    Project project = new Project(companyId, "Fixture project", "Fixture description");
                    project.setId(FixtureIds.PROJECT.getId());

                    Component parentComponent = new Component(null, "Fixture component (parent)", "Fixture description 1");
                    parentComponent.setId(FixtureIds.ROOT_COMPONENT.getId());
                    Component childComponent = new Component(parentComponent.getId(), "Fixture component (child)", "Fixture description 2");
                    childComponent.setId(FixtureIds.CHILD_COMPONENT.getId());
                    project.getComponents().addAll(Arrays.asList(parentComponent, childComponent));
                    Mono<Project> projectMono = projectRepository.save(project);

                    // job mono
                    Mono<Job> endpointJsonJobMono = createFixtureJobForComponent(parentComponent, JobType.ENDPOINT, EndpointResponseType.JSON);
                    Mono<Job> domJobMono = createFixtureJobForComponent(parentComponent, JobType.DOM_NODE, null);

                    // job + metrics
                    Flux<MetricsStats> metricsStatsParentFlux = createFixtureJobForComponent(parentComponent, JobType.STATUS_CODE, null)
                            .flatMapMany(this::createFixtureIncidentAndMetricsForJob);
                    Flux<MetricsStats> metricsStatsChildFlux = createFixtureJobForComponent(childComponent, JobType.ENDPOINT, EndpointResponseType.XML)
                            .flatMapMany(this::createFixtureIncidentAndMetricsForJob);

                    // combine everything into one mono
                    Mono<List<MetricsStats>> fixturesForProject = endpointJsonJobMono
                            .then(domJobMono)
                            .thenMany(metricsStatsParentFlux)
                            .thenMany(metricsStatsChildFlux)
                            .collectList();

                    return projectMono.zipWith(fixturesForProject, (p, metricsStats) -> p)
                            .doOnNext(p -> logger.info("Fixture project and test data are successfully created"));
                }));
    }

    private Mono<Job> createFixtureJobForComponent(Component component, JobType jobType, EndpointResponseType responseType) {
        final Job job = new Job(
                String.format("FixtureJob - %s%s | Component - %s", jobType,
                        responseType == null ? "" : " " + responseType, component.getName()),
                jobType,
                JobStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                null,
                component.getId(),
                LocalDateTime.now().minus(1, ChronoUnit.DAYS),
                null);

        switch (jobType) {
            case STATUS_CODE:
                job.setUrl("https://gitlab.infra.escalibre.net");
                job.setHttpStatusCode(302);
                break;
            case DOM_NODE:
                job.setUrl("https://www.google.com");
                job.setSelectorType(SelectorType.XPATH);
                job.setSelector("//*[@id=\"hplogo\"]");
                job.setEndOn(LocalDateTime.now());
                break;
            case ENDPOINT:
                job.setEndpointResponseType(responseType);
                if (EndpointResponseType.JSON == responseType) {
                    job.setUrl("https://jsonplaceholder.typicode.com/users");
                    job.setSelector("$.[0].name");
                } else {
                    job.setUrl("https://www.w3schools.com/xml/simple.xml");
                    job.setSelector("/breakfast_menu/food[1]/name");
                }
                break;
            case SCREENSHOT:
            default:
                return Mono.empty();
        }
        return jobRepository.save(job);
    }

    private Flux<MetricsStats> createFixtureIncidentAndMetricsForJob(Job job) {
        Mono<Incident> incidentMono = incidentRepository.save(new Incident(job.getComponentIds(),
                job.getId(),
                "Fixture incident",
                "Incident for job: " + job.getName(),
                IncidentStatus.RESOLVED,
                LocalDateTime.now().minus(1, ChronoUnit.DAYS),
                LocalDateTime.now()));

        Flux<MetricsStats> metricsStatsPerJobFlux = Flux.fromStream(Stream.iterate(0, i -> i + 1)
                .limit(METRICS_PER_JOB_AMOUNT)
                .map(i -> new MetricsStats(job.getId(),
                        MetricsStatus.SUCCESS,
                        "Info " + i,
                        LocalDateTime.now().minus(i, ChronoUnit.SECONDS))))
                .flatMap(metricsStats -> metricsStatsRepository.save(metricsStats));

        return incidentMono.thenMany(metricsStatsPerJobFlux);
    }
}

