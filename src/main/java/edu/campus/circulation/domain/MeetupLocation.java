package edu.campus.circulation.domain;

public record MeetupLocation(Campus campus, String place) {
    public MeetupLocation {
        if (campus == null) {
            throw new IllegalArgumentException("请选择交接校区。");
        }
        if (place == null || place.isBlank()) {
            throw new IllegalArgumentException("请填写具体交接地点。");
        }
        place = place.strip();
        if (place.codePointCount(0, place.length()) > 100 || place.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("交接地点须为单行文字，最多 100 字。");
        }
    }

    public String displayName() {
        return campus + " · " + place;
    }

    public enum Campus {
        NANHU("南湖校区"),
        GUIZISHAN("桂子山校区");

        private final String label;

        Campus(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
