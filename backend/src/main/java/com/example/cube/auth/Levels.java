package com.example.cube.auth;

import java.util.Locale;

/**
 * The reporting ladder, derived from a user's designation (job title):
 * Senior Vice President 7 > Vice President 6 > Senior Manager / Director 5 > Manager / Head 4 > Team Lead 3
 * > Senior Employee 2 > Employee, Consultant, Intern, ... 1.
 * Without a recognisable designation the access group decides: MANAGER = 4, EMPLOYEE = 1, ADMIN = 0.
 *
 * Rule: a person may only report to someone at Team Lead level or higher, and never to someone below their own level.
 * The same logic lives in the frontend (config.js designationLevel).
 */
public final class Levels {

    public static final int TEAM_LEAD = 3;
    public static final int MANAGER = 4;

    private Levels() {
    }

    public static int of(String designation, String groupCode) {
        String d = designation == null ? "" : designation.trim().toLowerCase(Locale.ROOT);
        if (!d.isEmpty()) {
            if (d.contains("senior vice president") || d.matches(".*\\bsvp\\b.*")) {
                return 7;
            }
            if (d.contains("vice president") || d.matches(".*\\bvp\\b.*")) {
                return 6;
            }
            if (d.contains("senior manager") || d.contains("director")) {
                return 5;
            }
            if (d.contains("manager") || d.contains("head")) {
                return 4;
            }
            if (d.contains("lead")) {
                return 3;
            }
            if (d.contains("senior")) {
                return 2;
            }
            if (!"MANAGER".equals(groupCode)) {
                return 1;
            }
        }
        if ("MANAGER".equals(groupCode)) {
            return MANAGER;
        }
        return "ADMIN".equals(groupCode) ? 0 : 1;
    }

    public static String name(int level) {
        switch (level) {
            case 7: return "Senior Vice President";
            case 6: return "Vice President";
            case 5: return "Senior Manager";
            case 4: return "Manager";
            case 3: return "Team Lead";
            case 2: return "Senior Employee";
            default: return "Employee";
        }
    }

    /** Null if allowed, otherwise the reason. */
    public static String checkReportsTo(String personName, int personLevel, String managerName, int managerLevel) {
        if (managerLevel < TEAM_LEAD) {
            return managerName + " is at " + name(managerLevel) + " level; people can only report to a Team Lead or higher";
        }
        if (managerLevel < personLevel) {
            return managerName + " (" + name(managerLevel) + ") is below " + personName + " (" + name(personLevel)
                    + "). Choose someone at the same level or higher";
        }
        return null;
    }
}
