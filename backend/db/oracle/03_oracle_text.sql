-- =====================================================================
-- Cube - Oracle Text index for search (cube.search.mode=oracle-text).
-- ONLY for a new environment: the existing CUBEDB already has CU_KNOWLEDGE_SEARCH_IDX.
--
-- Needs Oracle Text installed and (from a DBA):  GRANT CTXAPP TO CUBEDB;
-- If Oracle Text is not available, set CUBE_SEARCH_MODE=like instead.
-- =====================================================================

CREATE INDEX CU_KNOWLEDGE_SEARCH_IDX ON CU_KNOWLEDGE (SEARCH_TEXT)
    INDEXTYPE IS CTXSYS.CONTEXT
    PARAMETERS ('SYNC (ON COMMIT)');
