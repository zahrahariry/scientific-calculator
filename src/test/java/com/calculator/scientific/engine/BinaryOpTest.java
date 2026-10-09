package com.calculator.scientific.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryOpTest {

    @Test
    void basicArithmetic() {
        assertEquals(7.0, BinaryOp.ADD.apply(3, 4), 1e-12);
        assertEquals(-1.0, BinaryOp.SUB.apply(3, 4), 1e-12);
        assertEquals(12.0, BinaryOp.MUL.apply(3, 4), 1e-12);
        assertEquals(2.0, BinaryOp.DIV.apply(8, 4), 1e-12);
        assertEquals(1.0, BinaryOp.MOD.apply(10, 3), 1e-12);
    }

    @Test
    void powerAndRoot() {
        assertEquals(8.0, BinaryOp.POW.apply(2, 3), 1e-12);
        assertEquals(3.0, BinaryOp.YROOT.apply(27, 3), 1e-12);
    }

    @Test
    void divideByZeroMessages() {
        CalculatorException zero = assertThrows(CalculatorException.class, () -> BinaryOp.DIV.apply(1, 0));
        assertEquals("Cannot divide by zero", zero.getDisplayMessage());

        CalculatorException undefined = assertThrows(CalculatorException.class, () -> BinaryOp.DIV.apply(0, 0));
        assertEquals("Result is undefined", undefined.getDisplayMessage());
    }
}
