package backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping(value = "/api/component")
public class ComponentController {

    @Autowired
    private ComponentService componentService;

    @Autowired
    private ProjectService projectService;

    @PostMapping
    public Mono<ResponseEntity<Component>> addComponent(@RequestBody Component component) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> componentService.createComponent(projectId, component))
                .map(c -> new ResponseEntity<>(c, HttpStatus.CREATED))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public Flux<Component> getAllComponents() {
        return getCurrentUserFirstProjectId()
                .flatMapMany(projectId -> componentService.getAllComponents(projectId));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Component>> getComponentById(@PathVariable String id) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> componentService.findComponentById(projectId, id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Component>> update(@PathVariable("id") String id, @RequestBody Component component) {
        return getCurrentUserFirstProjectId()
                .flatMap(projectId -> componentService.updateComponent(projectId, id, component))
                .map(c -> new ResponseEntity<>(c, HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // TODO refactor when project context selection will be implemented, now it just takes first available project ID
    private Mono<String> getCurrentUserFirstProjectId() {
        return projectService.getCurrentUserProjects()
                .map(Project::getId)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Current user has no available projects")));
    }
}

