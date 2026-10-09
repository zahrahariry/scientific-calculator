package com.calculator.scientific.engine;

/**
 * Calculator domain error with a Windows Calculator-style display message.
 */
public class CalculatorException extends RuntimeException {

    private final String displayMessage;

    public CalculatorException(String displayMessage) {
        super(displayMessage);
        this.displayMessage = displayMessage;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }

    public static CalculatorException divideByZero() {
        return new CalculatorException("Cannot divide by zero");
    }

    public static CalculatorException undefined() {
        return new CalculatorException("Result is undefined");
    }

    public static CalculatorException invalidInput() {
        return new CalculatorException("Invalid input");
    }

    public static CalculatorException overflow() {
        return new CalculatorException("Overflow");
    }
}
