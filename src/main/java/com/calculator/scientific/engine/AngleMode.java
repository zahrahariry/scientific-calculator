package com.calculator.scientific.engine;

/**
 * Angle unit used by trigonometric functions (matches Windows Calculator).
 */
public enum AngleMode {
    DEG,
    RAD,
    GRAD;

    public AngleMode next() {
        return switch (this) {
            case DEG -> RAD;
            case RAD -> GRAD;
            case GRAD -> DEG;
        };
    }
}
