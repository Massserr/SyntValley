package dev.syntvalley.domain.memory;

/**
 * Where a memory came from. Only {@link #OBSERVED} is a fact the village directly witnessed; a player's
 * claim or a future LLM suggestion is recorded but must never be treated as an observed fact.
 */
public enum MemorySource {
    OBSERVED,
    PLAYER_SAID,
    LLM_SUGGESTED;

    public boolean isObservedFact() {
        return this == OBSERVED;
    }
}
