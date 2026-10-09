package com.calculator.scientific.engine;

/**
 * Immutable view of the calculator UI state returned to the browser.
 */
public record CalculatorSnapshot(
        String display,
        String expression,
        String angleMode,
        boolean scientificNotation,
        boolean secondFunction,
        boolean hyperbolic,
        boolean memoryHasValue,
        boolean error,
        String memoryDisplay
) {
}
