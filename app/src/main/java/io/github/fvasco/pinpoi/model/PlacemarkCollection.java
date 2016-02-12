package io.github.fvasco.pinpoi.model;

/**
 * A collection, aggregator for {@linkplain Placemark}
 *
 * @author Francesco Vasco
 */
public class PlacemarkCollection {

    private long id;
    private String name;
    private String description;
    private String category;
    private String source;
    private long lastUpdate;
    private int poiCount;

    public int getPoiCount() {
        return poiCount;
    }

    public void setPoiCount(int poiCount) {
        this.poiCount = poiCount;
    }

    /**
     * Last collection update, unix time
     */
    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return name;
    }
}
