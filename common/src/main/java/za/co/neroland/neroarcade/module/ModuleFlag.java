package za.co.neroland.neroarcade.module;

/**
 * Capabilities a module declares about itself. Bench warnings and the runtime's odd-combination defaults are
 * derived from flags alone, so a chip that declares its flags honestly gets correct warnings for free and no
 * code ever compares another chip's id.
 *
 * <p>Flags are never serialised. The last three constants extend the contract's list (see their docs):
 * without them three bench warnings could only be raised by comparing ids, which the contract forbids.
 */
public enum ModuleFlag {
    /** PICKUP: collecting it adds score. HAZARD / PAWN: the chip itself adds score (goals, cleared rows). */
    SCORES,
    /** RULE: cannot be won without a score source. */
    NEEDS_SCORE,
    /** RULE: places an EXIT cell at match start. */
    PLACES_EXIT,
    /** PICKUP: a key, consumed by PLACES_EXIT rules. */
    IS_KEY,
    /** RULE: the exit is locked until all keys are held. */
    NEEDS_KEY,
    /** PAWN: can emit projectiles when the intent asks. */
    FIRES,
    /** CONTROL: produces aim; pointless without FIRES. */
    NEEDS_AIM,
    /** PAWN or CONTROL: moves on one axis only. */
    ONE_AXIS,
    /** PAWN: moves without input. */
    AUTO_MOVE,
    /** HAZARD: damages. */
    LETHAL,
    /** PAWN: contact between pawns damages (versus duels). */
    LETHAL_TO_PAWNS,
    /** RULE or MULTIPLAYER: needs at least two seats to mean anything. */
    MULTI_SEAT_ONLY,
    /** MULTIPLAYER: can fill a missing seat with a runtime-driven pawn. */
    RUNTIME_OPPONENT,
    /** PICKUP: counts toward clearing the board. */
    CLEARABLE,
    /** CONTROL: produces no movement axes, only buttons (one-button control). Added for ONE_BUTTON_FREE_PAWN. */
    BUTTONS_ONLY,
    /** RULE: won by collecting every CLEARABLE pickup (clear-board). Added for CLEAR_BOARD_WITHOUT_PICKUPS. */
    NEEDS_CLEARABLE,
    /** RULE: won by outlasting the threats on a timer (survive-time). Added for NO_THREAT. */
    SURVIVAL
}
