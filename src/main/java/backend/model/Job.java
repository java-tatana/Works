package backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Document
public class Job {

    @Id
    private String id;
    private String name;
    private JobType type;
    private JobStatus status;
    private String url;
    private Integer httpStatusCode;
    private String selector;
    private SelectorType selectorType;
    private EndpointResponseType endpointResponseType;
    private byte[] image;
    private Set<String> componentIds = new HashSet<>();
    private LocalDateTime startOn;
    private LocalDateTime endOn;

    public Job() {
    }

    public Job(String name, JobType type, JobStatus status, String url, Integer httpStatusCode, String selector,
               SelectorType selectorType, EndpointResponseType endpointResponseType, byte[] image, String componentId,
               LocalDateTime startOn, LocalDateTime endOn) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.url = url;
        this.httpStatusCode = httpStatusCode;
        this.selector = selector;
        this.selectorType = selectorType;
        this.endpointResponseType = endpointResponseType;
        this.image = image;
        this.startOn = startOn;
        this.endOn = endOn;
        this.componentIds.add(componentId);
    }

    public Job(String name, JobType type, JobStatus status, String url, Integer httpStatusCode, String selector,
               SelectorType selectorType, EndpointResponseType endpointResponseType, byte[] image, Set<String> componentIds,
               LocalDateTime startOn, LocalDateTime endOn) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.url = url;
        this.httpStatusCode = httpStatusCode;
        this.selector = selector;
        this.selectorType = selectorType;
        this.endpointResponseType = endpointResponseType;
        this.image = image;
        this.componentIds.addAll(componentIds);
        this.startOn = startOn;
        this.endOn = endOn;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public SelectorType getSelectorType() {
        return selectorType;
    }

    public void setSelectorType(SelectorType selectorType) {
        this.selectorType = selectorType;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public Set<String> getComponentIds() {
        return componentIds;
    }

    public LocalDateTime getStartOn() {
        return startOn;
    }

    public void setStartOn(LocalDateTime startOn) {
        this.startOn = startOn;
    }

    public LocalDateTime getEndOn() {
        return endOn;
    }

    public void setEndOn(LocalDateTime endOn) {
        this.endOn = endOn;
    }

    public EndpointResponseType getEndpointResponseType() {
        return endpointResponseType;
    }

    public void setEndpointResponseType(EndpointResponseType endpointResponseType) {
        this.endpointResponseType = endpointResponseType;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", url='" + url + '\'' +
                ", httpStatusCode=" + httpStatusCode +
                ", selector='" + selector + '\'' +
                ", selectorType=" + selectorType +
                ", image=" + Arrays.toString(image) +
                ", componentIds=" + componentIds +
                ", startOn=" + startOn +
                ", endOn=" + endOn +
                '}';
    }
}

