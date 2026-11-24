package edu.campus.circulation.ui;

import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Offline campus geometries derived from OpenStreetMap, in WGS84. */
final class CampusMapLayout {
    static final int WIDTH = 850;
    static final int HEIGHT = 650;
    private static final double METRES_PER_DEGREE = 111_195;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<Campus, CampusMapLayout> CAMPUSES = Map.of(
            Campus.GUIZISHAN, load(Campus.GUIZISHAN, "/campus-map/guizishan.tsv"),
            Campus.NANHU, load(Campus.NANHU, "/campus-map/nanhu.tsv"));

    enum Layer { CAMPUS, WATER, GREEN, FIELD, TRACK, ROAD, STREET, BLOCK, BUILDING }

    private final Projection projection;
    private final List<Terrain> terrain;
    private final List<Footprint> footprints;

    private CampusMapLayout(Projection projection, List<Terrain> terrain, List<Footprint> footprints) {
        this.projection = projection;
        this.terrain = List.copyOf(terrain);
        this.footprints = List.copyOf(footprints);
    }

    static CampusMapLayout forCampus(Campus campus) {
        return Objects.requireNonNull(CAMPUSES.get(campus));
    }

    List<Terrain> terrain() {
        return terrain;
    }

    static List<Footprint> buildings(Campus campus) {
        return forCampus(campus).footprints;
    }

    static List<CampusBuilding> search(Campus campus, String query) {
        String term = normalize(query);
        return buildings(campus).stream().map(Footprint::building)
                .filter(building -> normalize(building.name()).contains(term)
                        || normalize(building.label()).contains(term)
                        || normalize(building.label()).replace("宿舍", "").contains(term)
                        || normalize(building.aliases()).contains(term))
                .sorted(Comparator.comparing((CampusBuilding building) -> !normalize(building.name()).equals(term)))
                .toList();
    }

    private static String normalize(String value) {
        return WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)).replaceAll("")
                .toLowerCase(Locale.ROOT).replace("学生宿舍", "宿舍");
    }

    static Footprint footprint(CampusBuilding building) {
        return buildings(building.campus()).stream().filter(entry -> entry.building() == building)
                .findFirst().orElseThrow();
    }

    static CampusBuilding hit(Campus campus, Point2D point) {
        return buildings(campus).stream().filter(entry -> entry.shape().contains(point))
                .map(Footprint::building).findFirst().orElse(null);
    }

    static CampusBuilding nearest(Campus campus, Point2D point) {
        return buildings(campus).stream().min(Comparator.comparingDouble(entry -> entry.distanceSquared(point)))
                .orElseThrow().building();
    }

    static Fix simulate(Campus campus, CampusBuilding preset) {
        var layout = forCampus(campus);
        var random = ThreadLocalRandom.current();
        Footprint target = preset == null ? layout.footprints.get(random.nextInt(layout.footprints.size())) : footprint(preset);
        double radius = layout.metresToMapUnits(preset == null ? 12 : 4);
        var point = new Point2D.Double(target.anchor().x + random.nextDouble(-radius, radius),
                target.anchor().y + random.nextDouble(-radius, radius));
        if (preset != null && !target.shape().contains(point)) {
            point = target.anchor();
        }
        GeoPoint coordinate = layout.projection.unproject(point);
        return new Fix(point.x, point.y, coordinate.latitude(), coordinate.longitude(), 6, nearest(campus, point));
    }

    double metresToMapUnits(double metres) {
        return metres * projection.unitsPerLatitudeDegree() / METRES_PER_DEGREE;
    }

    GeoPoint coordinateAt(Point2D point) {
        return projection.unproject(point);
    }

    private static CampusMapLayout load(Campus campus, String resource) {
        List<Row> rows;
        try (var reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(CampusMapLayout.class.getResourceAsStream(resource), resource), StandardCharsets.UTF_8))) {
            rows = reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).map(Row::parse).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取校园地图 " + resource, failure);
        }
        Row outline = rows.stream().filter(row -> row.layer() == Layer.CAMPUS).findFirst().orElseThrow();
        Projection projection = Projection.fit(outline.rings().get(0));
        var terrain = new ArrayList<Terrain>();
        var buildings = new ArrayList<Footprint>();
        for (Row row : rows) {
            List<List<Point2D.Double>> rings = row.rings().stream()
                    .map(ring -> ring.stream().map(projection::project).toList()).toList();
            Path2D.Double shape = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            for (var ring : rings) {
                shape.moveTo(ring.get(0).x, ring.get(0).y);
                for (var point : ring.subList(1, ring.size())) {
                    shape.lineTo(point.x, point.y);
                }
                if (row.layer() != Layer.ROAD && row.layer() != Layer.STREET) {
                    shape.closePath();
                }
            }
            if (row.layer() == Layer.BUILDING) {
                CampusBuilding building = CampusBuilding.valueOf(row.label());
                if (building.campus() != campus) {
                    throw new IllegalStateException("地图楼栋校区不匹配：" + building.name());
                }
                buildings.add(new Footprint(building, shape, rings, interiorPoint(shape)));
            } else {
                List<Point2D.Double> points = rings.get(0);
                var anchor = row.layer() == Layer.STREET ? points.get(points.size() / 2) : center(shape.getBounds2D());
                terrain.add(new Terrain(row.layer(), shape, row.label(), anchor));
            }
        }
        terrain.sort(Comparator.comparing(Terrain::layer));
        return new CampusMapLayout(projection, terrain, buildings);
    }

    private static Point2D.Double center(Rectangle2D bounds) {
        return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    private static Point2D.Double interiorPoint(Shape shape) {
        Rectangle2D bounds = shape.getBounds2D();
        Point2D.Double center = center(bounds);
        if (shape.contains(center)) {
            return center;
        }
        Point2D.Double nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int x = 1; x < 12; x++) {
            for (int y = 1; y < 12; y++) {
                var candidate = new Point2D.Double(bounds.getX() + bounds.getWidth() * x / 12,
                        bounds.getY() + bounds.getHeight() * y / 12);
                if (shape.contains(candidate) && candidate.distanceSq(center) < distance) {
                    nearest = candidate;
                    distance = candidate.distanceSq(center);
                }
            }
        }
        return Objects.requireNonNull(nearest, "地图楼栋轮廓没有可定位的内部区域");
    }

    record Terrain(Layer layer, Shape shape, String label, Point2D.Double anchor) {}

    record Fix(double x, double y, double latitude, double longitude, int accuracy, CampusBuilding building) {}

    record Footprint(CampusBuilding building, Shape shape, List<List<Point2D.Double>> rings, Point2D.Double anchor) {
        double distanceSquared(Point2D point) {
            if (shape.contains(point)) {
                return 0;
            }
            double distance = Double.POSITIVE_INFINITY;
            for (var ring : rings) {
                Point2D.Double previous = ring.get(ring.size() - 1);
                for (var vertex : ring) {
                    distance = Math.min(distance, Line2D.ptSegDistSq(previous.x, previous.y, vertex.x, vertex.y,
                            point.getX(), point.getY()));
                    previous = vertex;
                }
            }
            return distance;
        }
    }

    record GeoPoint(double latitude, double longitude) {
        private static GeoPoint parse(String pair) {
            String[] values = pair.split(",");
            return new GeoPoint(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
        }
    }

    private record Row(Layer layer, String label, List<List<GeoPoint>> rings) {
        private static Row parse(String line) {
            String[] fields = line.split("\t", 4);
            var rings = new ArrayList<List<GeoPoint>>();
            for (String ring : fields[3].split("\\|")) {
                rings.add(List.of(ring.split(" ")).stream().map(GeoPoint::parse).toList());
            }
            return new Row(Layer.valueOf(fields[0]), fields[2], rings);
        }
    }

    private record Projection(double latitude, double longitude, double unitsPerLatitudeDegree,
            double longitudeRatio) {
        private static Projection fit(List<GeoPoint> outline) {
            var lat = outline.stream().mapToDouble(GeoPoint::latitude).summaryStatistics();
            var lon = outline.stream().mapToDouble(GeoPoint::longitude).summaryStatistics();
            double latitude = (lat.getMin() + lat.getMax()) / 2;
            double longitude = (lon.getMin() + lon.getMax()) / 2;
            double ratio = Math.cos(Math.toRadians(latitude));
            double units = Math.min((WIDTH - 84) / ((lon.getMax() - lon.getMin()) * ratio),
                    (HEIGHT - 88) / (lat.getMax() - lat.getMin()));
            return new Projection(latitude, longitude, units, ratio);
        }

        private Point2D.Double project(GeoPoint point) {
            return new Point2D.Double(WIDTH / 2.0 + (point.longitude() - longitude) * longitudeRatio * unitsPerLatitudeDegree,
                    HEIGHT / 2.0 - (point.latitude() - latitude) * unitsPerLatitudeDegree);
        }

        private GeoPoint unproject(Point2D point) {
            return new GeoPoint(latitude + (HEIGHT / 2.0 - point.getY()) / unitsPerLatitudeDegree,
                    longitude + (point.getX() - WIDTH / 2.0) / (unitsPerLatitudeDegree * longitudeRatio));
        }
    }
}
