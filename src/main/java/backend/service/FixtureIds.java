package backend.service;



public enum FixtureIds {

    USER("fixture40e0548abe04b9e00"), // id of fixture user in auth module, we expecting that it already created
    COMPANY("fixture40e0548abe04b9e01"),
    ACCOUNT("fixture40e0548abe04b9e02"),
    PROJECT("fixture40e0548abe04b9e03"),
    ROOT_COMPONENT("fixture40e0548abe04b9e04"),
    CHILD_COMPONENT("fixture40e0548abe04b9e05");

    private String id;

    FixtureIds(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
