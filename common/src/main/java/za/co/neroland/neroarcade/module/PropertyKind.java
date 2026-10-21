package za.co.neroland.neroarcade.module;

/**
 * The only three property kinds a module may declare. No strings, no floats, no lists, no nested objects: a
 * fractional quantity is an INT percent or divisor, a choice is an ENUM.
 */
public enum PropertyKind {
    INT,
    BOOL,
    ENUM
}
