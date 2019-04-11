package backend.controller;


import backend.model.Incident;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/api/incident")
public class IncidentController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private IncidentService incidentService;

    @GetMapping
    public Mono<PageResponse<Incident>> getIncidentsSortedByPage(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "sortFields", defaultValue = "name") String sortFields,
            @RequestParam(value = "sortDirection", defaultValue = "asc") String sortDirection,
            @RequestParam(value = "filters", required = false) String filters) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> {
                    Flux<Incident> incidentList = incidentService.retrieveAllIncidentsPaged(projectId, filters,
                            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.fromString(sortDirection), sortFields)));
                    Mono<Long> incidentCount = incidentService.countAllIncidents(projectId, filters);
                    return incidentList
                            .collectList()
                            .zipWith(incidentCount, (incidents, count) -> new PageResponse<>(page, count, pageSize, sortFields, sortDirection, incidents));
                });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Incident>> getIncidentById(@PathVariable String id) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> incidentService.findIncidentById(projectId, id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Mono<ResponseEntity<Incident>> createIncident(@RequestBody Incident incident) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> incidentService.createIncident(projectId, incident))
                .map(i -> new ResponseEntity<>(i, HttpStatus.CREATED))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Incident>> updateIncident(@PathVariable("id") String id, @RequestBody Incident incident) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> incidentService.updateIncident(projectId, id, incident))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("component/{componentId}")
    public Flux<Incident> getAllIncidentsByComponentId(@PathVariable String componentId) {
        return getCurrentUserFirstProjectId()
                .flatMapMany(projectId -> incidentService.findAllIncidentsByComponentId(projectId, componentId));
    }

    @GetMapping("/active")
    public Flux<Incident> getAllActiveIncidents() {
        return getCurrentUserFirstProjectId()
                .flatMapMany(projectId -> incidentService.findAllActiveIncidents(projectId));
    }

    // TODO refactor when project context selection will be implemented, now it just takes first available project ID
    private Mono<String> getCurrentUserFirstProjectId() {
        return projectService.getCurrentUserProjects()
                .map(Project::getId)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Current user has no available projects")));
    }
}

