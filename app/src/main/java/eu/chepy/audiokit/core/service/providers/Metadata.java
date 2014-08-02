package eu.chepy.audiokit.core.service.providers;

public class Metadata {

    public int index;

    public int description;

    public String value;

    public boolean editable;

    public Metadata(int index, int description, String value, boolean editable) {
        this.index = index;
        this.description = description;
        this.value = value;
        this.editable = editable;
    }
}
