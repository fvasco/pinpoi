package io.github.fvasco.pinpoi.model;

import java.util.Collection;

import io.github.fvasco.pinpoi.util.Coordinates;

/**
 * @author Placemark result with annotation information.
 *         Used by {@linkplain io.github.fvasco.pinpoi.dao.PlacemarkDao#findAllPlacemarkNear(Coordinates, double, Collection)}
 */
public final class PlacemarkSearchResult extends Coordinates implements PlacemarkBase {
    private final long id;
    private final String name;
    private final boolean flagged;


    public PlacemarkSearchResult(final long id, float latitude, float longitude, final String name, final boolean flagged) {
        super(latitude, longitude);
        this.id = id;
        this.name = name;
        this.flagged = flagged;
    }

    public String getName() {
        return name;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public long getId() {

        return id;
    }

}
