package backend.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Document
public class Incident {

    @Id
    private String id;
    private String jobId;
    private String name;
    private String description;
    private IncidentStatus status;
    private LocalDateTime dateStart;
    private LocalDateTime dateEnd;
    private Set<String> componentIds = new HashSet<>();
    private LocalDateTime updatedAt;

    public Incident() {
    }

    public Incident(String componentId, String jobId, String name, String description, IncidentStatus status,
                    LocalDateTime dateStart, LocalDateTime dateEnd) {
        this.componentIds.add(componentId);
        this.jobId = jobId;
        this.name = name;
        this.description = description;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
        this.status = status;
    }

    public Incident(Set<String> componentIds, String jobId, String name, String description,
                    IncidentStatus status, LocalDateTime dateStart, LocalDateTime dateEnd) {
        this.componentIds.addAll(componentIds);
        this.jobId = jobId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDateStart() {
        return dateStart;
    }

    public void setDateStart(LocalDateTime dateStart) {
        this.dateStart = dateStart;
    }

    public LocalDateTime getDateEnd() {
        return dateEnd;
    }

    public void setDateEnd(LocalDateTime dateEnd) {
        this.dateEnd = dateEnd;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Set<String> getComponentIds() {
        return componentIds;
    }
}

