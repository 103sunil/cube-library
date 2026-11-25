package com.example.cube.auth;

/**
 * Permission codes are "MODULE_CODE:ACCESS_CODE" and match the rows already in the CUBEDB
 * schema (CU_MODULE_MASTER / CU_ACCESS_MASTER, each access code belongs to one module).
 */
public final class Permissions {

    public static final String MODULE_KNOWLEDGE = "KNOWLEDGE";
    public static final String MODULE_AUTH = "AUTH";

    /** Browse and open published knowledge. */
    public static final String KNOWLEDGE_VIEW = "KNOWLEDGE:VIEW";
    /** Search published knowledge. */
    public static final String KNOWLEDGE_SEARCH = "KNOWLEDGE:SEARCH";
    /** Download / preview attachments of knowledge the user can view. */
    public static final String KNOWLEDGE_DOWNLOAD = "KNOWLEDGE:DOWNLOAD";
    /** Create knowledge and submit it for review. */
    public static final String KNOWLEDGE_SUBMIT = "KNOWLEDGE:CREATE";
    /** Review queue: approve, reject, archive. Submissions by approvers publish directly. */
    public static final String KNOWLEDGE_APPROVE = "KNOWLEDGE:APPROVE";
    /** Edit any knowledge item. */
    public static final String KNOWLEDGE_EDIT = "KNOWLEDGE:UPDATE";
    /** Delete any knowledge item. */
    public static final String KNOWLEDGE_DELETE = "KNOWLEDGE:DELETE";

    public static final String MANAGE_USERS = "AUTH:MANAGE_USERS";
    public static final String MANAGE_GROUPS = "AUTH:MANAGE_GROUPS";
    public static final String MANAGE_MODULES = "AUTH:MANAGE_MODULES";
    public static final String MANAGE_ACCESS = "AUTH:MANAGE_ACCESS";
    public static final String MANAGE_GROUP_ACCESS = "AUTH:MANAGE_GROUP_ACCESS";
    public static final String MANAGE_USER_ACCESS = "AUTH:MANAGE_USER_ACCESS";

    /** Any of these opens /api/v1/admin/**; each endpoint then checks its own code. */
    public static final String[] ANY_ADMIN = {
            MANAGE_USERS, MANAGE_GROUPS, MANAGE_MODULES, MANAGE_ACCESS, MANAGE_GROUP_ACCESS, MANAGE_USER_ACCESS
    };

    private Permissions() {
    }

    public static String code(String moduleCode, String accessCode) {
        return moduleCode + ":" + accessCode;
    }
}
