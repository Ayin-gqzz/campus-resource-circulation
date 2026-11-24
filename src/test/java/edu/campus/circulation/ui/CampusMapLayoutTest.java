package edu.campus.circulation.ui;

import static edu.campus.circulation.domain.CampusBuilding.*;
import static edu.campus.circulation.domain.MeetupLocation.Campus.GUIZISHAN;
import static edu.campus.circulation.domain.MeetupLocation.Campus.NANHU;
import static org.junit.jupiter.api.Assertions.*;

import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampusMapLayoutTest {
    @Test
    void searchesNamesAliasesAndCodesInTheActiveCampus() {
        assertEquals(List.of(D05), CampusMapLayout.search(GUIZISHAN, " ｄ０５ "));
        assertEquals(List.of(D05, E05), CampusMapLayout.search(GUIZISHAN, "5栋"));
        assertEquals(List.of(T09), CampusMapLayout.search(GUIZISHAN, "九号楼"));
        assertEquals(List.of(LIB), CampusMapLayout.search(GUIZISHAN, "图书馆"));
        assertEquals(List.of(NLIB), CampusMapLayout.search(NANHU, "图书馆"));
        assertTrue(CampusMapLayout.search(NANHU, "D05").isEmpty());
    }

    @Test
    void everyBuildingHasAClickableFootprintAndDeterministicPreset() {
        for (CampusBuilding building : CampusBuilding.values()) {
            var footprint = CampusMapLayout.footprint(building);
            assertEquals(building, CampusMapLayout.hit(building.campus(), footprint.anchor()));
            var fix = CampusMapLayout.simulate(building.campus(), building);
            assertEquals(building, fix.building());
            assertEquals(6, fix.accuracy());
        }
        for (Campus campus : Campus.values()) {
            assertEquals(campus, CampusMapLayout.simulate(campus, null).building().campus());
        }
    }

    @Test
    void findsTheNearestFootprintWhenTheFixIsOutside() {
        var bounds = CampusMapLayout.footprint(D05).shape().getBounds2D();
        var point = new Point2D.Double(bounds.getMinX() - 0.1, bounds.getCenterY());
        assertNull(CampusMapLayout.hit(GUIZISHAN, point));
        assertEquals(D05, CampusMapLayout.nearest(GUIZISHAN, point));
    }

    @Test
    void keepsCampusAndDormitoryPositionsInTheirWgs84Ranges() {
        var western = CampusMapLayout.forCampus(GUIZISHAN).coordinateAt(CampusMapLayout.footprint(D05).anchor());
        var eastern = CampusMapLayout.forCampus(GUIZISHAN).coordinateAt(CampusMapLayout.footprint(E05).anchor());
        var nanhu = CampusMapLayout.forCampus(NANHU).coordinateAt(CampusMapLayout.footprint(N05).anchor());
        assertTrue(western.latitude() > 30.5195 && western.latitude() < 30.5205);
        assertTrue(western.longitude() > 114.352 && western.longitude() < 114.354);
        assertTrue(eastern.longitude() > western.longitude());
        assertTrue(nanhu.latitude() > 30.506 && nanhu.latitude() < 30.512);
        assertEquals(36, java.util.Arrays.stream(CampusBuilding.values())
                .filter(building -> building.kind() == CampusBuilding.Kind.DORM).count());
    }
}
