package com.example.cube.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

/** What the current user may do with this item. The UI uses it to show or hide buttons. */
@Getter
@Setter
public class KnowledgeActions {

    private boolean canEdit;
    private boolean canSubmit;
    private boolean canApprove;
    private boolean canReject;
    private boolean canArchive;
    private boolean canRestore;
    private boolean canDelete;
    private boolean canDownload;
    private boolean canManageAttachments;
}
