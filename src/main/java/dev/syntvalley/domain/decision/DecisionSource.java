package dev.syntvalley.domain.decision;

/**
 * Who made a decision. Today every decision is DETERMINISTIC (the Java planner). The same audit format
 * will later accept LLM_ADVISED entries, so the log can always show whether Java or the advisor chose.
 */
public enum DecisionSource {
    DETERMINISTIC,
    LLM_ADVISED
}
