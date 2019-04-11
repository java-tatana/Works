package backend.service;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;

public class JobExecutionServiceImplTest extends AbstractSpringTest {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JobRepository jobRepository;

    @MockBean
    private WebClient webClient;

    @Test
    public void findValueInJsonResponseTest() {
        Job job = new Job("FindJSON", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "$[0].address.zipcode", null,
                EndpointResponseType.JSON, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("json/users.json");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.SUCCESS, metricsStats.getStatus()))
                .verifyComplete();
    }

    @Test
    public void cannotFindValueInJsonResponseTest() {
        Job job = new Job("FindJSON", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "$[0].address.town", null,
                EndpointResponseType.JSON, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("json/users.json");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus()))
                .verifyComplete();
    }

    @Test
    public void findValueInXmlResponseTest() {
        Job job = new Job("FindXML", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "/current/temperature/@value", null,
                EndpointResponseType.XML, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("xml/weather.xml");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.SUCCESS, metricsStats.getStatus()))
                .verifyComplete();

    }

    @Test
    public void cannotFindValueInXmlResponseTest() {
        Job job = new Job("FindXML", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "/current/notToBeFound/@value", null,
                EndpointResponseType.XML, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("xml/weather.xml");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus()))
                .verifyComplete();

    }

    @Test
    public void wrongEndpointTypeTest() {
        Job job = new Job("FindXML", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "/current/temperature/@value", null,
                null, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("xml/weather.xml");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus()))
                .verifyComplete();
    }

    @Test
    public void corruptedJsonPathTest() {
        Job job = new Job("FindJSON", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "$[0]!@#$%^&*(*&*(&*(&DSDaddress.town", null,
                EndpointResponseType.JSON, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("json/users.json");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus()))
                .verifyComplete();
    }

    @Test
    public void corruptedXpathTest() {
        Job job = new Job("FindXML", JobType.ENDPOINT, JobStatus.PENDING,
                "url", null, "!@#$%^&*()_(**&@/current/temperature/@value", null,
                EndpointResponseType.XML, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseBody("xml/weather.xml");

        StepVerifier.create(jobExecutionService.executeJob(job))
                .assertNext(metricsStats -> Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus()))
                .verifyComplete();
    }

    @Test
    public void shouldCreateSuccessMetricOnStatusCodeJob() {
        HttpStatus expectedStatus = HttpStatus.OK;
        Job job = new Job("TestJob", JobType.STATUS_CODE, JobStatus.RUNNING,
                "testLink", expectedStatus.value(), null,
                null, null, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseStatus(expectedStatus);

        StepVerifier.create(jobRepository.save(job)
                .flatMap(j -> jobExecutionService.executeJob(j)))
                .assertNext(metricsStats -> {
                    Assert.assertEquals(MetricsStatus.SUCCESS, metricsStats.getStatus());
                    Assert.assertEquals(job.getId(), metricsStats.getJobId());
                })
                .verifyComplete();
    }

    @Test
    public void shouldCreateFailMetricOnStatusCodeJob() {
        HttpStatus expectedStatus = HttpStatus.OK;
        Job job = new Job("TestJob", JobType.STATUS_CODE, JobStatus.RUNNING,
                "testLink", expectedStatus.value(), null,
                null, null, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseStatus(HttpStatus.BAD_REQUEST);

        StepVerifier.create(jobRepository.save(job)
                .flatMap(j -> jobExecutionService.executeJob(j)))
                .assertNext(metricsStats -> {
                    Assert.assertEquals(MetricsStatus.FAIL, metricsStats.getStatus());
                    Assert.assertEquals(job.getId(), metricsStats.getJobId());
                })
                .verifyComplete();
    }


    @Test
    public void shouldAddNewIncidentOnJobFailTest() {
        HttpStatus expectedStatus = HttpStatus.GATEWAY_TIMEOUT;
        Job job = new Job("TestJob", JobType.STATUS_CODE, JobStatus.RUNNING,
                "testLink", HttpStatus.OK.value(), null,
                null, null, null, "123", LocalDateTime.now(), null);

        mockWebClientResponseStatus(expectedStatus);
        StepVerifier.create(jobRepository.save(job)
                .flatMap(j -> jobExecutionService.executeJob(j))
                .flatMap(metricsStats -> incidentRepository.findByJobId(job.getId()).next()))
                .assertNext(incident -> {
                    Assert.assertEquals(job.getId(), incident.getJobId());
                    Assert.assertEquals(IncidentStatus.OPENED, incident.getStatus());
                    Assert.assertEquals(job.getComponentIds(), incident.getComponentIds());
                    Assert.assertEquals("Incident for job: " + job.getName(), incident.getName());
                    Assert.assertNull(incident.getDateEnd());
                })
                .expectComplete()
                .verify();

    }

    @Test
    public void shouldUpdateIncidentOnJobFailTest() {
        HttpStatus expectedStatus = HttpStatus.GATEWAY_TIMEOUT;
        Job job = new Job("TestJob", JobType.STATUS_CODE, JobStatus.RUNNING,
                "testLink", HttpStatus.OK.value(), null,
                null, null, null, "123", LocalDateTime.now(), null);

        Incident incident = new Incident(job.getComponentIds(), job.getId(),
                "Incident for job: " + job.getName(), "Incident info",
                IncidentStatus.RESOLVED, LocalDateTime.now(), LocalDateTime.now());

        mockWebClientResponseStatus(expectedStatus);
        StepVerifier.create(jobRepository.save(job)
                .flatMap(j -> {
                    incident.setJobId(j.getId());
                    return incidentRepository.save(incident).zipWith(Mono.just(j));
                })
                .flatMap(tuple -> jobExecutionService.executeJob(tuple.getT2()))
                .flatMap(metricsStats -> incidentRepository.findByJobId(metricsStats.getJobId()).next().zipWith(Mono.just(metricsStats))))
                .assertNext(incidentMetricsStatsTuple -> {
                    Assert.assertEquals(incidentMetricsStatsTuple.getT2().getJobId(), incidentMetricsStatsTuple.getT1().getJobId());
                    Assert.assertEquals(IncidentStatus.OPENED, incidentMetricsStatsTuple.getT1().getStatus());
                    Assert.assertEquals(incidentMetricsStatsTuple.getT2().getDetailedInfo(), incidentMetricsStatsTuple.getT1().getDescription());
                    Assert.assertNotNull(incidentMetricsStatsTuple.getT1().getUpdatedAt());
                    Assert.assertNull(incidentMetricsStatsTuple.getT1().getDateEnd());
                })
                .expectComplete()
                .verify();
    }

    @Test
    public void shouldUpdateIncidentOnJobSuccessTest() {
        HttpStatus expectedStatus = HttpStatus.OK;
        Job job = new Job("TestJob", JobType.STATUS_CODE, JobStatus.RUNNING,
                "testLink", HttpStatus.OK.value(), null,
                null, null, null, "123", LocalDateTime.now(), null);

        Incident incident = new Incident(job.getComponentIds(), job.getId(),
                "Incident for job: " + job.getName(), "Incident info",
                IncidentStatus.OPENED, LocalDateTime.now(), null);

        mockWebClientResponseStatus(expectedStatus);
        StepVerifier.create(jobRepository.save(job)
                .flatMap(j -> {
                    incident.setJobId(j.getId());
                    return incidentRepository.save(incident).zipWith(Mono.just(j));
                })
                .flatMap(tuple -> jobExecutionService.executeJob(tuple.getT2()))
                .flatMap(metricsStats -> incidentRepository.findByJobId(metricsStats.getJobId()).next().zipWith(Mono.just(metricsStats))))
                .assertNext(incidentMetricsStatsTuple -> {
                    Assert.assertEquals(incidentMetricsStatsTuple.getT2().getJobId(), incidentMetricsStatsTuple.getT1().getJobId());
                    Assert.assertEquals(IncidentStatus.RESOLVED, incidentMetricsStatsTuple.getT1().getStatus());
                    Assert.assertNotNull(incidentMetricsStatsTuple.getT1().getUpdatedAt());
                    Assert.assertNotNull(incidentMetricsStatsTuple.getT1().getDateEnd());
                })
                .expectComplete()
                .verify();
    }

    private void mockWebClientResponseStatus(HttpStatus expectedStatus) {
        WebClient.RequestHeadersUriSpec<?> requestUriSpecMock = Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        Mockito.doReturn(requestUriSpecMock).when(webClient).get();
        Mockito.doReturn(requestUriSpecMock).when(requestUriSpecMock).uri(Mockito.anyString());
        Mockito.doReturn(requestUriSpecMock).when(requestUriSpecMock).accept(MediaType.ALL);
        Mockito.when(requestUriSpecMock.exchange()).thenReturn(Mono.just(ClientResponse.create(expectedStatus).build()));
    }


    private void mockWebClientResponseBody(String responseFile) {
        String response = getResourceAsString(responseFile);

        WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock = Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.ResponseSpec responseSpecMock = Mockito.mock(WebClient.ResponseSpec.class);

        Mockito.doReturn(requestHeadersUriSpecMock).when(webClient).get();
        Mockito.doReturn(requestHeadersUriSpecMock).when(requestHeadersUriSpecMock).uri(Mockito.anyString());

        if (responseFile.endsWith(".json")) {
            Mockito.doReturn(requestHeadersUriSpecMock).when(requestHeadersUriSpecMock).accept(MediaType.APPLICATION_JSON);
        } else if (responseFile.endsWith(".xml")) {
            Mockito.doReturn(requestHeadersUriSpecMock).when(requestHeadersUriSpecMock).accept(MediaType.APPLICATION_XML);
        }

        Mockito.doReturn(responseSpecMock).when(requestHeadersUriSpecMock).retrieve();
        Mockito.doReturn(Mono.just(response)).when(responseSpecMock).bodyToMono(String.class);
    }

    private String getResourceAsString(String resourcePath) {
        Resource usersJson = new ClassPathResource(resourcePath);
        try {
            return new String(Files.readAllBytes(usersJson.getFile().toPath()), Charset.forName("UTF-8"));
        } catch (IOException e) {
            return "";
        }
    }
}

