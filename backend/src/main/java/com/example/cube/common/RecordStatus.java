package com.example.cube.common;

/** Flag values used by the CU_ master tables. */
public final class RecordStatus {

    public static final String ACTIVE = "A";
    public static final String INACTIVE = "I";
    /** Users only: removed (e.g. left the company). Kept because they authored knowledge; hidden and cannot sign in. */
    public static final String REMOVED = "D";
    public static final String YES = "Y";
    public static final String NO = "N";

    private RecordStatus() {
    }

    public static boolean isValidStatus(String value) {
        return ACTIVE.equals(value) || INACTIVE.equals(value);
    }
}
