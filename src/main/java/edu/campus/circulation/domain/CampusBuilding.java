package edu.campus.circulation.domain;

import static edu.campus.circulation.domain.MeetupLocation.Campus.GUIZISHAN;
import static edu.campus.circulation.domain.MeetupLocation.Campus.NANHU;

import edu.campus.circulation.domain.MeetupLocation.Campus;

/** Stable building codes for the campus map. The codes are assigned by this application. */
public enum CampusBuilding {
    D01(GUIZISHAN, "西区宿舍1栋", Kind.DORM),
    D02(GUIZISHAN, "西区宿舍2栋", Kind.DORM),
    D03(GUIZISHAN, "西区宿舍3栋", Kind.DORM),
    D04(GUIZISHAN, "西区宿舍4栋", Kind.DORM),
    D05(GUIZISHAN, "西区宿舍5栋", Kind.DORM),
    D06(GUIZISHAN, "西区宿舍6栋", Kind.DORM),
    D07(GUIZISHAN, "西区宿舍7栋", Kind.DORM),
    D08(GUIZISHAN, "西区宿舍8栋", Kind.DORM),
    E01(GUIZISHAN, "东区宿舍1栋", Kind.DORM),
    E02(GUIZISHAN, "东区宿舍2栋", Kind.DORM),
    E03(GUIZISHAN, "东区宿舍3栋", Kind.DORM),
    E04(GUIZISHAN, "东区宿舍4栋", Kind.DORM),
    E05(GUIZISHAN, "东区宿舍5栋", Kind.DORM),
    E06(GUIZISHAN, "东区宿舍6栋", Kind.DORM),
    E07(GUIZISHAN, "东区宿舍7栋", Kind.DORM),
    E08(GUIZISHAN, "东区宿舍8栋", Kind.DORM),
    E09(GUIZISHAN, "东区宿舍9栋", Kind.DORM),
    E10(GUIZISHAN, "东区宿舍10栋", Kind.DORM),
    E11(GUIZISHAN, "东区宿舍11栋", Kind.DORM),
    E12(GUIZISHAN, "东区宿舍12栋", Kind.DORM),
    E13(GUIZISHAN, "东区宿舍13栋", Kind.DORM),
    E14(GUIZISHAN, "东区宿舍14栋", Kind.DORM),
    E16(GUIZISHAN, "东区宿舍16栋", Kind.DORM),
    T07(GUIZISHAN, "7号教学楼", Kind.TEACHING),
    T08(GUIZISHAN, "8号教学楼", Kind.TEACHING),
    T09(GUIZISHAN, "9号教学楼", Kind.TEACHING, "九号楼 九号教学楼"),
    TJB(GUIZISHAN, "田家炳教学楼", Kind.TEACHING),
    LIB(GUIZISHAN, "图书馆主馆", Kind.LIBRARY, "桂子山图书馆"),
    GXU(GUIZISHAN, "桂香园食堂", Kind.DINING),
    QYC(GUIZISHAN, "沁园春食堂", Kind.DINING),
    N01(NANHU, "南湖宿舍1栋", Kind.DORM, "南湖一栋"),
    N02(NANHU, "南湖宿舍2栋", Kind.DORM, "南湖二栋"),
    N03(NANHU, "南湖宿舍3栋", Kind.DORM, "南湖三栋"),
    N04(NANHU, "南湖宿舍4栋", Kind.DORM, "南湖四栋"),
    N05(NANHU, "南湖宿舍5栋", Kind.DORM, "南湖五栋"),
    N06(NANHU, "南湖宿舍6栋", Kind.DORM, "南湖六栋"),
    N07(NANHU, "南湖宿舍7栋", Kind.DORM, "南湖七栋"),
    N08(NANHU, "南湖宿舍8栋", Kind.DORM, "南湖八栋"),
    N09(NANHU, "南湖宿舍9栋", Kind.DORM, "南湖九栋"),
    N10(NANHU, "南湖宿舍10栋", Kind.DORM, "南湖十栋"),
    N11(NANHU, "南湖宿舍11栋", Kind.DORM, "南湖十一栋"),
    N12(NANHU, "南湖宿舍12栋", Kind.DORM, "南湖十二栋"),
    N13(NANHU, "南湖宿舍13栋", Kind.DORM, "南湖十三栋"),
    NCOM(NANHU, "南湖综合楼", Kind.TEACHING, "南湖教学实验综合楼"),
    NLIB(NANHU, "图书馆南湖分馆", Kind.LIBRARY, "南湖图书馆"),
    NFOOD(NANHU, "南湖食堂", Kind.DINING);

    public enum Kind { DORM, TEACHING, LIBRARY, DINING }

    private final Campus campus;
    private final String label;
    private final Kind kind;
    private final String aliases;

    CampusBuilding(Campus campus, String label, Kind kind) {
        this(campus, label, kind, "");
    }

    CampusBuilding(Campus campus, String label, Kind kind, String aliases) {
        this.campus = campus;
        this.label = label;
        this.kind = kind;
        this.aliases = aliases;
    }

    public Campus campus() {
        return campus;
    }

    public Kind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public String aliases() {
        return aliases;
    }

    public String displayName() {
        return label + " (" + name() + ")";
    }
}
