package com.calculator.scientific.engine;

/**
 * Binary operators with Windows Scientific precedence.
 * Higher precedence value is evaluated first. Equal precedence is left-to-right,
 * except power / y-root which associate right-to-left.
 */
public enum BinaryOp {
    ADD(1, "+", false),
    SUB(1, "−", false),
    MUL(2, "×", false),
    DIV(2, "÷", false),
    MOD(2, "mod", false),
    POW(3, "^", true),
    YROOT(3, "ʸ√", true);

    private final int precedence;
    private final String symbol;
    private final boolean rightAssociative;

    BinaryOp(int precedence, String symbol, boolean rightAssociative) {
        this.precedence = precedence;
        this.symbol = symbol;
        this.rightAssociative = rightAssociative;
    }

    public int getPrecedence() {
        return precedence;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isRightAssociative() {
        return rightAssociative;
    }

    public double apply(double left, double right) {
        return switch (this) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> {
                if (right == 0.0) {
                    if (left == 0.0) {
                        throw CalculatorException.undefined();
                    }
                    throw CalculatorException.divideByZero();
                }
                yield left / right;
            }
            case MOD -> {
                if (right == 0.0) {
                    throw CalculatorException.undefined();
                }
                yield left % right;
            }
            case POW -> {
                if (left < 0 && right != Math.rint(right)) {
                    throw CalculatorException.invalidInput();
                }
                if (left == 0.0 && right < 0) {
                    throw CalculatorException.divideByZero();
                }
                double result = Math.pow(left, right);
                if (Double.isNaN(result)) {
                    throw CalculatorException.invalidInput();
                }
                if (Double.isInfinite(result)) {
                    throw CalculatorException.overflow();
                }
                yield result;
            }
            case YROOT -> {
                if (right == 0.0) {
                    throw CalculatorException.divideByZero();
                }
                if (left < 0 && right % 2 == 0) {
                    throw CalculatorException.invalidInput();
                }
                double result = Math.pow(left, 1.0 / right);
                if (Double.isNaN(result)) {
                    // Odd root of negative via abs
                    if (left < 0 && Math.abs(right - Math.rint(right)) < 1e-12
                            && ((long) Math.rint(right)) % 2 != 0) {
                        yield -Math.pow(-left, 1.0 / right);
                    }
                    throw CalculatorException.invalidInput();
                }
                if (Double.isInfinite(result)) {
                    throw CalculatorException.overflow();
                }
                yield result;
            }
        };
    }
}
