package backend.service;



import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


@Service
public class JobExecutionServiceImpl implements JobExecutionService {

    @Autowired
    private Logger logger;

    @Autowired
    private WebClient webClient;

    @Autowired
    private MetricsStatsRepository metricsStatsRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Value("${selenium.hub.url}")
    private String seleniumHubUrl;

    @Value("${selenium.pageload.wait.timeout: 5}")
    private int pageLoadWaitTimeout;

    @Override
    public Mono<MetricsStats> executeJob(Job job) {
        return Mono.defer(() -> {
            switch (job.getType()) {
                case DOM_NODE:
                    logger.info(String.format("Starting DOM Monitoring job: %s, id: %s", job.getName(), job.getId()));
                    return domMonitoringJob(job);
                case STATUS_CODE:
                    logger.info(String.format("Starting Status Code monitoring job: %s, id: %s", job.getName(), job.getId()));
                    return statusCodeMonitoringJob(job);
                case ENDPOINT:
                    logger.info(String.format("Starting Endpoint monitoring job: %s, id: %s", job.getName(), job.getId()));
                    return endpointMonitoringJob(job);
                case SCREENSHOT:
                default:
                    return Mono.empty();
            }
        }).flatMap(metricsStats -> saveJobResults(metricsStats, job));
    }

    private Mono<MetricsStats> saveJobResults(MetricsStats metricsStats, Job job) {
        return incidentRepository.findByJobId(job.getId())
                .next()
                .flatMap(incident -> {
                    switch (metricsStats.getStatus()) {
                        case FAIL:
                            if (IncidentStatus.RESOLVED == incident.getStatus()) {
                                incident.setDateStart(LocalDateTime.now());
                            }
                            incident.setStatus(IncidentStatus.OPENED);
                            incident.setDescription(metricsStats.getDetailedInfo());
                            incident.setUpdatedAt(LocalDateTime.now());
                            incident.setDateEnd(null);
                            break;
                        case SUCCESS:
                            if (IncidentStatus.RESOLVED != incident.getStatus()) {
                                incident.setStatus(IncidentStatus.RESOLVED);
                                incident.setDateEnd(LocalDateTime.now());
                                incident.setUpdatedAt(LocalDateTime.now());
                            }
                            break;
                        default:
                            break;
                    }
                    return incidentRepository.save(incident);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (MetricsStatus.FAIL == metricsStats.getStatus()) {
                        Incident newIncident = new Incident(job.getComponentIds(), job.getId(),
                                "Incident for job: " + job.getName(), metricsStats.getDetailedInfo(),
                                IncidentStatus.OPENED, LocalDateTime.now(), null);
                        return incidentRepository.save(newIncident);
                    } else {
                        return Mono.empty();
                    }
                }))
                .then(metricsStatsRepository.save(metricsStats));
    }

    private Mono<MetricsStats> domMonitoringJob(Job job) {
        WebDriver webDriver = null;
        MetricsStatus status;
        String msg;
        try {
            webDriver = new RemoteWebDriver(new URL(seleniumHubUrl), new ChromeOptions());
            logger.info(String.format("Connected to WebDriver Job - %s id%s", job.getName(), job.getId()));
            WebDriverWait webDriverWait = new WebDriverWait(webDriver, pageLoadWaitTimeout);
            // load page
            logger.info(String.format("Loading page %s Job - %s id%s", job.getUrl(), job.getName(), job.getId()));
            webDriver.get(job.getUrl());
            // wait 5 sec or until required element is visible
            webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(job.getSelector())));
            webDriver.findElement(By.xpath(job.getSelector()));
            msg = String.format("DOM contains specified element. URL: %s Job - %s id%s", job.getUrl(), job.getName(),
                    job.getId());
            logger.info(msg);
            status = MetricsStatus.SUCCESS;
        } catch (NoSuchElementException noSuchElementException) {
            msg = String.format("DOM does not contain specified element. URL: %s Job - %s id%s", job.getUrl(),
                    job.getName(), job.getId());
            logger.warn(msg);
            status = MetricsStatus.FAIL;
        } catch (Exception e) {
            msg = e.getMessage();
            status = MetricsStatus.FAIL;
        } finally {
            if (webDriver != null) {
                webDriver.quit();
            }
        }
        logger.info(String.format("Saving MetricStats into DB. Finishing ... Job - %s id%s", job.getName(), job.getId()));
        return Mono.just(new MetricsStats(job.getId(), status, msg));
    }

    private Mono<MetricsStats> statusCodeMonitoringJob(Job job) {
        return webClient
                .get()
                .uri(job.getUrl())
                .accept(MediaType.ALL)
                .exchange()
                .timeout(Duration.of(3, ChronoUnit.SECONDS))
                .map(clientResponse -> {
                    if (clientResponse.statusCode().value() == job.getHttpStatusCode()) {
                        logger.info(String.format("URL %s is accessible Job - %s id%s", job.getUrl(), job.getName(),
                                job.getId()));
                        return new MetricsStats(job.getId(), MetricsStatus.SUCCESS,
                                String.format("URL %s is accessible", job.getUrl()));
                    } else {
                        logger.warn(String.format("URL %s is not accessible, status code: %s Job - %s id%s", job.getUrl(),
                                clientResponse.statusCode(), job.getName(), job.getId()));
                        return new MetricsStats(job.getId(), MetricsStatus.FAIL,
                                String.format("URL %s is not accessible, status code: %s", job.getUrl(),
                                        clientResponse.statusCode()));
                    }
                })
                .onErrorResume(throwable -> {
                    logger.error(String.format("Error. Details: %s Job - %s id%s",
                            throwable.getClass().getName() + ": " + throwable.getMessage(), job.getName(), job.getId()));
                    return Mono.just(new MetricsStats(job.getId(), MetricsStatus.FAIL,
                            throwable.getClass().getName() + ": " + throwable.getMessage()));
                });
    }

    private Mono<MetricsStats> endpointMonitoringJob(Job job) {
        return webClient
                .get()
                .uri(job.getUrl())
                .accept(EndpointResponseType.JSON == job.getEndpointResponseType() ?
                        MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    String extractedValue;
                    try {
                        if (EndpointResponseType.JSON == job.getEndpointResponseType()) {
                            // extract value using JSONPath
                            extractedValue = JsonPath.parse(body).read(job.getSelector());
                        } else if (EndpointResponseType.XML == job.getEndpointResponseType()) {
                            // extract value using XPATH
                            final Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                                    .parse(new InputSource(new StringReader(body)));
                            final XPathExpression xpath =
                                    XPathFactory.newInstance().newXPath().compile(job.getSelector());
                            extractedValue = xpath.evaluate(xmlDocument);
                            if (extractedValue.isEmpty()) {
                                return Mono.error(new Exception(String.format("XPath '%s' not found.", job.getSelector())));
                            }
                        } else {
                            return Mono.error(new Exception("Wrong endpoint response type"));
                        }
                    } catch (PathNotFoundException | ParserConfigurationException
                            | SAXException | IOException | XPathExpressionException e) {
                        return Mono.error(e);
                    }
                    return Mono.just(new MetricsStats(job.getId(), MetricsStatus.SUCCESS,
                            String.format("Extracted value: %s", extractedValue)));
                })
                .onErrorResume(throwable -> {
                    logger.error(String.format("Error. Details: %s Job - %s id%s",
                            throwable.getClass().getName() + ": " + throwable.getMessage(), job.getName(), job.getId()));
                    return Mono.just(new MetricsStats(job.getId(), MetricsStatus.FAIL,
                            String.format("%s: %s", throwable.getClass().getName(), throwable.getMessage())));
                });
    }
}
