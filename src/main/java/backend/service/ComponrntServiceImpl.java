package backend.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
public class ComponentServiceImpl implements ComponentService {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Override
    public Mono<Component> createComponent(String projectId, Component newComponent) {
        Component component = new Component();
        updateComponentInfo(component, newComponent);
        return projectService.getProjectById(projectId)
                .flatMap(project -> {
                    project.getComponents().add(component);
                    return saveProjectChanges(project);
                })
                .flatMap(project -> Mono.just(component));
    }

    @Override
    public Flux<Component> getAllComponents(String projectId) {
        return projectService.getProjectById(projectId)
                .flatMapIterable(Project::getComponents);
    }

    @Override
    public Flux<Component> getAllComponentsWithChilds(String projectId) {
        Flux<Component> componentFlux = projectService
                .getProjectById(projectId)
                .flatMapIterable(Project::getComponents);

        return componentFlux
                .expand(f -> findByParentId(componentFlux, f.getId()));
    }

    @Override
    public Flux<Component> getAllComponentsByIdWithChilds(String projectId, String componentId) {
        Flux<Component> componentFlux = projectService
                .getProjectById(projectId)
                .flatMapIterable(Project::getComponents);

        return componentFlux
                .filter(c -> c.getId().equals(componentId))
                .expand(f -> findByParentId(componentFlux, f.getId()));
    }


    private Flux<Component> findByParentId(Flux<Component> componentFlux, String componentId) {
        return componentFlux
                .filter(component -> component.getParentId() != null)
                .filter(cid -> componentId.equals(cid.getParentId()));
    }

    @Override
    public Mono<Component> findComponentById(String projectId, String componentId) {
        return projectService.getProjectById(projectId)
                .flatMapIterable(Project::getComponents)
                .filter(cid -> cid.getId().equals(componentId)).singleOrEmpty();
    }

    @Override
    public Mono<Component> updateComponent(String projectId, String componentId, Component updatedComponent) {
        return projectService.getProjectById(projectId)
                .flatMap(project -> {
                    Optional<Component> existingComponent = project.getComponents().stream()
                            .filter(component -> component.getId().equals(componentId))
                            .findFirst();
                    if (existingComponent.isPresent()) {
                        updateComponentInfo(existingComponent.get(), updatedComponent);
                        return saveProjectChanges(project).flatMap(p -> Mono.just(updatedComponent));
                    } else {
                        return Mono.empty();
                    }
                });
    }

    private Mono<Project> saveProjectChanges(Project project) {
        return projectRepository.save(project);
    }

    private void updateComponentInfo(Component component, Component newComponent) {
        component.setDescription(newComponent.getDescription());
        component.setName(newComponent.getName());
        component.setParentId(newComponent.getParentId());
    }
}

