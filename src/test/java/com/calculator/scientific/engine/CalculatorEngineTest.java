package com.calculator.scientific.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorEngineTest {

    private CalculatorEngine calc;

    @BeforeEach
    void setUp() {
        calc = new CalculatorEngine();
    }

    private void keys(String... commands) {
        for (String command : commands) {
            calc.input(command);
        }
    }

    private double displayValue() {
        CalculatorSnapshot snap = calc.snapshot();
        assertFalse(snap.error(), () -> "Unexpected error: " + snap.display());
        return Double.parseDouble(snap.display());
    }

    @Test
    void addition() {
        keys("DIGIT_2", "ADD", "DIGIT_3", "EQUALS");
        assertEquals(5.0, displayValue(), 1e-12);
    }

    @Test
    void precedenceMultiplyBeforeAdd() {
        // 2 + 3 × 4 = 14
        keys("DIGIT_2", "ADD", "DIGIT_3", "MUL", "DIGIT_4", "EQUALS");
        assertEquals(14.0, displayValue(), 1e-12);
    }

    @Test
    void parentheses() {
        // (2 + 3) × 4 = 20
        keys("LPAREN", "DIGIT_2", "ADD", "DIGIT_3", "RPAREN", "MUL", "DIGIT_4", "EQUALS");
        assertEquals(20.0, displayValue(), 1e-12);
    }

    @Test
    void power() {
        // 2 ^ 10 = 1024
        keys("DIGIT_2", "POW", "DIGIT_1", "DIGIT_0", "EQUALS");
        assertEquals(1024.0, displayValue(), 1e-12);
    }

    @Test
    void powerRightAssociative() {
        // 2 ^ 3 ^ 2 = 2^(3^2) = 512
        keys("DIGIT_2", "POW", "DIGIT_3", "POW", "DIGIT_2", "EQUALS");
        assertEquals(512.0, displayValue(), 1e-12);
    }

    @Test
    void divideByZero() {
        keys("DIGIT_1", "DIV", "DIGIT_0", "EQUALS");
        CalculatorSnapshot snap = calc.snapshot();
        assertTrue(snap.error());
        assertEquals("Cannot divide by zero", snap.display());
    }

    @Test
    void zeroDividedByZeroUndefined() {
        keys("DIGIT_0", "DIV", "DIGIT_0", "EQUALS");
        CalculatorSnapshot snap = calc.snapshot();
        assertTrue(snap.error());
        assertEquals("Result is undefined", snap.display());
    }

    @Test
    void sqrtNegativeInvalid() {
        keys("DIGIT_1", "NEGATE", "SQRT");
        CalculatorSnapshot snap = calc.snapshot();
        assertTrue(snap.error());
        assertEquals("Invalid input", snap.display());
    }

    @Test
    void squareAndSqrt() {
        keys("DIGIT_9", "SQRT");
        assertEquals(3.0, displayValue(), 1e-12);
        keys("SQUARE");
        assertEquals(9.0, displayValue(), 1e-12);
    }

    @Test
    void cubeAndCbrt() {
        keys("DIGIT_2", "CUBE");
        assertEquals(8.0, displayValue(), 1e-12);
        keys("CBRT");
        assertEquals(2.0, displayValue(), 1e-12);
    }

    @Test
    void reciprocal() {
        keys("DIGIT_4", "RECIPROCAL");
        assertEquals(0.25, displayValue(), 1e-12);
    }

    @Test
    void factorial() {
        keys("DIGIT_5", "FACTORIAL");
        assertEquals(120.0, displayValue(), 1e-12);
    }

    @Test
    void logAndLn() {
        keys("DIGIT_1", "DIGIT_0", "DIGIT_0", "LOG");
        assertEquals(2.0, displayValue(), 1e-12);
        calc.clearAll();
        keys("E", "LN");
        assertEquals(1.0, displayValue(), 1e-9);
    }

    @Test
    void sinCosDeg() {
        keys("DEG", "DIGIT_3", "DIGIT_0", "SIN");
        assertEquals(0.5, displayValue(), 1e-12);
        calc.clearAll();
        keys("DEG", "DIGIT_6", "DIGIT_0", "COS");
        assertEquals(0.5, displayValue(), 1e-12);
    }

    @Test
    void sinRad() {
        // sin(π/2) in radians ≈ 1
        keys("RAD", "PI", "DIV", "DIGIT_2", "EQUALS", "SIN");
        assertEquals(1.0, displayValue(), 1e-12);
    }

    @Test
    void inverseSin() {
        keys("DEG", "DIGIT_0", "DECIMAL", "DIGIT_5", "ASIN");
        assertEquals(30.0, displayValue(), 1e-9);
    }

    @Test
    void hyperbolicSinh() {
        keys("DIGIT_0", "SINH");
        assertEquals(0.0, displayValue(), 1e-12);
    }

    @Test
    void mod() {
        keys("DIGIT_1", "DIGIT_0", "MOD", "DIGIT_3", "EQUALS");
        assertEquals(1.0, displayValue(), 1e-12);
    }

    @Test
    void yRoot() {
        // 8 with yroot 3 => 2
        keys("DIGIT_8", "YROOT", "DIGIT_3", "EQUALS");
        assertEquals(2.0, displayValue(), 1e-12);
    }

    @Test
    void absAndNegate() {
        keys("DIGIT_5", "NEGATE", "ABS");
        assertEquals(5.0, displayValue(), 1e-12);
    }

    @Test
    void percentOfOperand() {
        // 200 + 10% = 220
        keys("DIGIT_2", "DIGIT_0", "DIGIT_0", "ADD", "DIGIT_1", "DIGIT_0", "PERCENT", "EQUALS");
        assertEquals(220.0, displayValue(), 1e-12);
    }

    @Test
    void memoryRoundTrip() {
        keys("DIGIT_4", "DIGIT_2", "MS");
        assertTrue(calc.snapshot().memoryHasValue());
        keys("C", "MR");
        assertEquals(42.0, displayValue(), 1e-12);
        keys("DIGIT_8", "M_PLUS", "MR");
        assertEquals(50.0, displayValue(), 1e-12);
        keys("DIGIT_5", "M_MINUS", "MR");
        assertEquals(45.0, displayValue(), 1e-12);
        keys("MC");
        assertFalse(calc.snapshot().memoryHasValue());
    }

    @Test
    void clearEntryKeepsExpression() {
        keys("DIGIT_2", "ADD", "DIGIT_3", "CE", "DIGIT_5", "EQUALS");
        assertEquals(7.0, displayValue(), 1e-12);
    }

    @Test
    void clearResetsEverything() {
        keys("DIGIT_9", "ADD", "DIGIT_1", "C");
        CalculatorSnapshot snap = calc.snapshot();
        assertEquals("0", snap.display());
        assertEquals("", snap.expression());
    }

    @Test
    void backspace() {
        keys("DIGIT_1", "DIGIT_2", "DIGIT_3", "BACKSPACE", "BACKSPACE");
        assertEquals(1.0, displayValue(), 1e-12);
    }

    @Test
    void piConstant() {
        keys("PI", "DIV", "PI", "EQUALS");
        assertEquals(1.0, displayValue(), 1e-12);
    }

    @Test
    void exp10() {
        keys("DIGIT_3", "EXP10");
        assertEquals(1000.0, displayValue(), 1e-12);
    }

    @Test
    void scientificNotationToggle() {
        keys("DIGIT_1", "DIGIT_0", "DIGIT_0", "FE");
        CalculatorSnapshot snap = calc.snapshot();
        assertTrue(snap.scientificNotation());
        assertTrue(snap.display().toLowerCase().contains("e"));
    }

    @Test
    void evaluateExpressionHelper() {
        assertEquals(27.0, calc.evaluateExpression("(4*3/6+1)*3^2"), 1e-12);
        calc.clearAll();
        assertEquals(16.0, calc.evaluateExpression("8/2*(2+2)"), 1e-12);
    }

    @Test
    void complexPrecedence() {
        // 18 ÷ (8 − 2 × 3) = 9
        keys("DIGIT_1", "DIGIT_8", "DIV", "LPAREN", "DIGIT_8", "SUB", "DIGIT_2", "MUL", "DIGIT_3", "RPAREN", "EQUALS");
        assertEquals(9.0, displayValue(), 1e-12);
    }
}
