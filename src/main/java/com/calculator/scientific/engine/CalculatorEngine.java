package com.calculator.scientific.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Stateful scientific calculator engine modeled on Windows Calculator Scientific mode.
 * Processes button/key commands and maintains display, expression history, memory, and angle mode.
 */
public class CalculatorEngine {

    private static final int MAX_DIGITS = 16;
    private static final double MEMORY_EPSILON = 1e-15;

    private AngleMode angleMode = AngleMode.DEG;
    private boolean scientificNotation;
    private boolean secondFunction;
    private boolean hyperbolic;

    private String entry = "0";
    private boolean entering;
    private boolean overwrite;
    private boolean error;
    private String errorMessage;

    private final Deque<Double> values = new ArrayDeque<>();
    private final Deque<BinaryOp> ops = new ArrayDeque<>();
    private final Deque<Integer> parenDepthMarkers = new ArrayDeque<>();
    private int openParens;

    private String expression = "";
    private boolean expressionComplete;
    private double lastResult;
    private BinaryOp lastBinaryOp;
    private Double lastOperand;
    private boolean afterEquals;

    private double memory;
    private boolean memorySet;

    public synchronized CalculatorSnapshot snapshot() {
        return new CalculatorSnapshot(
                error ? errorMessage : formatDisplay(currentValue()),
                expression,
                angleMode.name(),
                scientificNotation,
                secondFunction,
                hyperbolic,
                memorySet && Math.abs(memory) > MEMORY_EPSILON,
                error,
                memorySet ? formatNumber(memory) : ""
        );
    }

    public synchronized CalculatorSnapshot clearAll() {
        resetWorkingState();
        expression = "";
        expressionComplete = false;
        afterEquals = false;
        lastBinaryOp = null;
        lastOperand = null;
        return snapshot();
    }

    public synchronized CalculatorSnapshot clearEntry() {
        if (error) {
            return clearAll();
        }
        entry = "0";
        entering = false;
        overwrite = true;
        return snapshot();
    }

    public synchronized CalculatorSnapshot input(String command) {
        if (command == null || command.isBlank()) {
            return snapshot();
        }
        String cmd = command.trim();

        // Mode toggles work even in error state
        switch (cmd) {
            case "DEG", "RAD", "GRAD" -> {
                angleMode = AngleMode.valueOf(cmd);
                return snapshot();
            }
            case "TOGGLE_ANGLE" -> {
                angleMode = angleMode.next();
                return snapshot();
            }
            case "FE" -> {
                scientificNotation = !scientificNotation;
                return snapshot();
            }
            case "2ND" -> {
                secondFunction = !secondFunction;
                return snapshot();
            }
            case "HYP" -> {
                hyperbolic = !hyperbolic;
                return snapshot();
            }
            case "C" -> {
                return clearAll();
            }
            case "CE" -> {
                return clearEntry();
            }
            default -> {
            }
        }

        if (error) {
            // Digits / constants restart after error; other keys stay locked like Windows
            if (isDigitCommand(cmd) || "DECIMAL".equals(cmd) || "PI".equals(cmd) || "E".equals(cmd)) {
                clearAll();
            } else if ("BACKSPACE".equals(cmd)) {
                return clearAll();
            } else {
                return snapshot();
            }
        }

        try {
            dispatch(cmd);
        } catch (CalculatorException ex) {
            setError(ex.getDisplayMessage());
        } catch (ArithmeticException ex) {
            setError("Invalid input");
        }

        return snapshot();
    }

    private void dispatch(String cmd) {
        if (isDigitCommand(cmd)) {
            inputDigit(cmd.equals("DIGIT_00") ? "00" : cmd.substring(cmd.length() - 1));
            return;
        }

        switch (cmd) {
            case "DECIMAL" -> inputDecimal();
            case "BACKSPACE" -> backspace();
            case "NEGATE" -> negate();
            case "PERCENT" -> percent();
            case "EQUALS" -> equals();
            case "ADD" -> binary(BinaryOp.ADD);
            case "SUB" -> binary(BinaryOp.SUB);
            case "MUL" -> binary(BinaryOp.MUL);
            case "DIV" -> binary(BinaryOp.DIV);
            case "MOD" -> binary(BinaryOp.MOD);
            case "POW" -> binary(BinaryOp.POW);
            case "YROOT" -> binary(BinaryOp.YROOT);
            case "LPAREN" -> openParen();
            case "RPAREN" -> closeParen();
            case "PI" -> constant(Math.PI, "π");
            case "E" -> constant(Math.E, "e");
            case "MC" -> memoryClear();
            case "MR" -> memoryRecall();
            case "M_PLUS" -> memoryAdd();
            case "M_MINUS" -> memorySubtract();
            case "MS" -> memoryStore();
            case "RECIPROCAL" -> unary(this::reciprocal, "1/({})");
            case "SQUARE" -> unary(v -> Math.pow(v, 2), "sqr({})");
            case "CUBE" -> unary(v -> Math.pow(v, 3), "cube({})");
            case "SQRT" -> unary(this::sqrt, "√({})");
            case "CBRT" -> unary(Math::cbrt, "∛({})");
            case "ABS" -> unary(Math::abs, "abs({})");
            case "FACTORIAL" -> unary(this::factorial, "fact({})");
            case "EXP10" -> unary(v -> Math.pow(10, v), "10^({})");
            case "EXP2" -> unary(v -> Math.pow(2, v), "2^({})");
            case "EXP" -> unary(Math::exp, "e^({})");
            case "LOG" -> unary(this::log10, "log({})");
            case "LN" -> unary(this::ln, "ln({})");
            case "EXP_NOTATION" -> startExponentEntry();
            case "SIN" -> trig("sin");
            case "COS" -> trig("cos");
            case "TAN" -> trig("tan");
            case "ASIN" -> inverseTrig("asin");
            case "ACOS" -> inverseTrig("acos");
            case "ATAN" -> inverseTrig("atan");
            case "SINH" -> unary(Math::sinh, "sinh({})");
            case "COSH" -> unary(Math::cosh, "cosh({})");
            case "TANH" -> unary(Math::tanh, "tanh({})");
            case "ASINH" -> unary(this::asinh, "asinh({})");
            case "ACOSH" -> unary(this::acosh, "acosh({})");
            case "ATANH" -> unary(this::atanh, "atanh({})");
            default -> {
                // Ignore unknown commands
            }
        }
    }

    private boolean isDigitCommand(String cmd) {
        return cmd.startsWith("DIGIT_") || "DIGIT_00".equals(cmd);
    }

    private void inputDigit(String digits) {
        if (afterEquals || expressionComplete) {
            expression = "";
            expressionComplete = false;
            values.clear();
            ops.clear();
            parenDepthMarkers.clear();
            openParens = 0;
            afterEquals = false;
        }
        if (!entering || overwrite) {
            entry = digits.startsWith("0") && digits.length() > 1 ? digits.replaceFirst("^0+(?!$)", "") : digits;
            if (entry.isEmpty()) {
                entry = "0";
            }
            // Fresh entry: replace leading 0 unless starting "0."
            if ("0".equals(entry) && digits.equals("0")) {
                entry = "0";
            } else if (entry.equals("0") && !digits.equals("0")) {
                entry = digits.replaceFirst("^0+", "");
                if (entry.isEmpty()) {
                    entry = "0";
                }
            }
            entering = true;
            overwrite = false;
        } else {
            if (digitCount(entry) >= MAX_DIGITS) {
                return;
            }
            if (entry.equals("0") && !digits.equals("0")) {
                entry = digits;
            } else if (!(entry.equals("0") && digits.equals("0"))) {
                entry = entry + digits;
            }
        }
    }

    private void inputDecimal() {
        if (afterEquals || expressionComplete) {
            expression = "";
            expressionComplete = false;
            values.clear();
            ops.clear();
            afterEquals = false;
        }
        if (!entering || overwrite) {
            entry = "0.";
            entering = true;
            overwrite = false;
        } else if (!entry.contains(".") && !entry.toLowerCase(Locale.ROOT).contains("e")) {
            entry = entry + ".";
        }
    }

    private void backspace() {
        if (overwrite || afterEquals || !entering) {
            return;
        }
        if (entry.length() <= 1 || (entry.startsWith("-") && entry.length() == 2)) {
            entry = "0";
            entering = false;
        } else {
            entry = entry.substring(0, entry.length() - 1);
        }
    }

    private void negate() {
        if (error) {
            return;
        }
        if (entering && !overwrite) {
            if (entry.startsWith("-")) {
                entry = entry.substring(1);
            } else if (!entry.equals("0") && !entry.equals("0.")) {
                entry = "-" + entry;
            } else {
                setCurrentValue(-currentValue(), false);
                overwrite = true;
            }
            return;
        }
        setCurrentValue(-currentValue(), false);
        overwrite = true;
        entering = false;
    }

    private void percent() {
        // Windows Scientific: percentage relative to pending left operand when a binary op is active
        double v = currentValue();
        if (!ops.isEmpty() && !values.isEmpty()) {
            double base = values.peekLast();
            v = base * (v / 100.0);
        } else {
            v = v / 100.0;
        }
        setCurrentValue(v, false);
        overwrite = true;
        entering = false;
    }

    private void binary(BinaryOp op) {
        if (afterEquals) {
            expression = formatNumber(lastResult);
            values.clear();
            ops.clear();
            values.addLast(lastResult);
            afterEquals = false;
            expressionComplete = false;
        } else if (entering) {
            double v = currentValue();
            appendOperandToExpression(v);
            values.addLast(v);
        } else if (values.isEmpty()) {
            double v = currentValue();
            appendOperandToExpression(v);
            values.addLast(v);
        } else if (ops.size() >= values.size()) {
            // Pending operator with no new operand yet — replace operator
            ops.removeLast();
            trimTrailingOperatorFromExpression();
        }
        // else: value already on stack (e.g. after ')') — just attach the operator

        reduceWhile(op);
        ops.addLast(op);
        expression = expression.trim() + " " + op.getSymbol() + " ";
        entering = false;
        overwrite = true;
        expressionComplete = false;
        pendingConstantSymbol = null;
        pendingConstantValue = null;
    }

    private void equals() {
        if (afterEquals && lastBinaryOp != null && lastOperand != null) {
            // Repeat last operation: result op operand
            double result = lastBinaryOp.apply(lastResult, lastOperand);
            checkFinite(result);
            expression = formatNumber(lastResult) + " " + lastBinaryOp.getSymbol() + " " + formatNumber(lastOperand) + " =";
            lastResult = result;
            setCurrentValue(result, false);
            expressionComplete = true;
            return;
        }

        double v = currentValue();
        if (entering || values.isEmpty()) {
            appendOperandToExpression(v);
            values.addLast(v);
        } else if (ops.size() >= values.size()) {
            // Binary operator waiting for its right-hand operand
            appendOperandToExpression(v);
            values.addLast(v);
        }
        // else: value already on the stack (e.g. result of a closed parenthesis)

        // Close any open parentheses implicitly
        while (openParens > 0) {
            closeParenInternal(false);
        }

        reduceAll();
        double result = values.isEmpty() ? v : values.removeLast();
        checkFinite(result);
        ops.clear();

        if (!expression.endsWith("=")) {
            expression = expression.trim() + " =";
        }
        lastResult = result;
        setCurrentValue(result, false);
        values.clear();
        ops.clear();
        parenDepthMarkers.clear();
        openParens = 0;
        entering = false;
        overwrite = true;
        afterEquals = true;
        expressionComplete = true;
    }

    private void openParen() {
        if (afterEquals) {
            expression = "";
            values.clear();
            ops.clear();
            afterEquals = false;
            expressionComplete = false;
        }
        // Implicit multiply: 2( -> 2 × (
        if ((entering || (!overwrite && !values.isEmpty() && ops.isEmpty())) && !expression.isBlank()
                && !expression.endsWith("(") && !expression.endsWith(" ")) {
            // If we have a bare number waiting, push multiply
            if (entering) {
                double v = currentValue();
                appendOperandToExpression(v);
                values.addLast(v);
                entering = false;
            }
            reduceWhile(BinaryOp.MUL);
            ops.addLast(BinaryOp.MUL);
            expression = expression + " " + BinaryOp.MUL.getSymbol() + " ";
        } else if (!entering && !values.isEmpty() && ops.size() < values.size()) {
            reduceWhile(BinaryOp.MUL);
            ops.addLast(BinaryOp.MUL);
            expression = expression + " " + BinaryOp.MUL.getSymbol() + " ";
        }

        expression = expression + "(";
        parenDepthMarkers.addLast(ops.size());
        openParens++;
        overwrite = true;
        entering = false;
    }

    private void closeParen() {
        if (openParens <= 0) {
            return;
        }
        closeParenInternal(true);
    }

    private void closeParenInternal(boolean updateExpression) {
        double v = currentValue();
        if (entering || values.isEmpty() || values.size() == ops.size()) {
            if (updateExpression && entering) {
                appendOperandToExpression(v);
            } else if (updateExpression && !expression.endsWith(")") && !endsWithNumberLiteral()) {
                appendOperandToExpression(v);
            }
            values.addLast(v);
            entering = false;
        }

        int targetOps = parenDepthMarkers.isEmpty() ? 0 : parenDepthMarkers.removeLast();
        while (ops.size() > targetOps) {
            reduceOnce();
        }
        openParens = Math.max(0, openParens - 1);
        if (updateExpression) {
            expression = expression + ")";
        }
        if (!values.isEmpty()) {
            setCurrentValue(values.peekLast(), false);
        }
        overwrite = true;
        entering = false;
    }

    private boolean endsWithNumberLiteral() {
        String t = expression.trim();
        if (t.isEmpty()) {
            return false;
        }
        char c = t.charAt(t.length() - 1);
        return Character.isDigit(c) || c == '.' || c == 'π' || c == 'e' || c == ')';
    }

    private void constant(double value, String symbol) {
        if (afterEquals || expressionComplete) {
            expression = "";
            values.clear();
            ops.clear();
            afterEquals = false;
            expressionComplete = false;
        }
        setCurrentValue(value, false);
        entry = formatNumber(value);
        entering = false;
        overwrite = true;
        // Constants show in expression when an operator follows; keep as current value
        // Mark as if entered
        entering = true;
        entry = symbol.equals("π") ? "π" : "e";
        // Store numeric in a side channel — use special handling via currentValue
        // Simpler: set entry to formatted number but expression uses symbol when appended
        setCurrentValue(value, false);
        overwrite = true;
        entering = false;
        // Push as pending display value; when used in binary, append symbol
        this.pendingConstantSymbol = symbol;
        this.pendingConstantValue = value;
        setCurrentValue(value, false);
        overwrite = true;
    }

    private String pendingConstantSymbol;
    private Double pendingConstantValue;

    private void unary(java.util.function.DoubleUnaryOperator fn, String pattern) {
        double v = currentValue();
        String operandText = pendingConstantSymbol != null ? pendingConstantSymbol : formatNumber(v);
        if (pendingConstantSymbol != null) {
            pendingConstantSymbol = null;
            pendingConstantValue = null;
        }
        double result = fn.applyAsDouble(v);
        checkFinite(result);

        String wrapped = pattern.replace("{}", operandText);
        if (expressionComplete || afterEquals) {
            expression = wrapped;
            afterEquals = false;
            expressionComplete = false;
            values.clear();
            ops.clear();
        } else if (!ops.isEmpty() && !entering && !overwrite) {
            expression = expression + wrapped;
        } else {
            // Replace trailing operand in expression if present, else append
            if (endsWithNumberLiteral() && !expression.isBlank() && !expression.trim().endsWith(")")) {
                expression = stripTrailingOperand(expression) + wrapped;
            } else if (expression.endsWith(")")) {
                expression = wrapped.replace("{}", expression);
            } else {
                expression = expression + wrapped;
            }
        }

        setCurrentValue(result, false);
        entering = false;
        overwrite = true;
        pendingConstantSymbol = null;
    }

    private String stripTrailingOperand(String expr) {
        String t = expr.trim();
        // Remove trailing number / constant
        int i = t.length() - 1;
        if (t.charAt(i) == 'π' || t.charAt(i) == 'e') {
            return t.substring(0, i).stripTrailing() + (t.substring(0, i).isBlank() ? "" : " ");
        }
        while (i >= 0 && (Character.isDigit(t.charAt(i)) || t.charAt(i) == '.' || t.charAt(i) == '-'
                || t.charAt(i) == 'E' || t.charAt(i) == 'e' || t.charAt(i) == '+')) {
            // careful with 'e' constant vs scientific notation — for display numbers we use formatNumber without 'e' const
            i--;
        }
        String head = t.substring(0, i + 1).stripTrailing();
        return head.isEmpty() ? "" : head + (head.endsWith("(") ? "" : " ");
    }

    private void trig(String name) {
        if (hyperbolic) {
            switch (name) {
                case "sin" -> unary(Math::sinh, "sinh({})");
                case "cos" -> unary(Math::cosh, "cosh({})");
                case "tan" -> unary(Math::tanh, "tanh({})");
                default -> {
                }
            }
            return;
        }
        switch (name) {
            case "sin" -> unary(v -> Math.sin(toRadians(v)), "sin({}" + angleSuffix() + ")");
            case "cos" -> unary(v -> Math.cos(toRadians(v)), "cos({}" + angleSuffix() + ")");
            case "tan" -> unary(v -> {
                double rad = toRadians(v);
                // Near 90° + 180k in degrees → undefined
                double deg = toDegreesFromMode(v);
                double mod = Math.IEEEremainder(deg, 180.0);
                if (Math.abs(Math.abs(mod) - 90.0) < 1e-10) {
                    throw CalculatorException.invalidInput();
                }
                double result = Math.tan(rad);
                if (Double.isNaN(result) || Double.isInfinite(result)) {
                    throw CalculatorException.invalidInput();
                }
                return result;
            }, "tan({}" + angleSuffix() + ")");
            default -> {
            }
        }
    }

    private void inverseTrig(String name) {
        if (hyperbolic) {
            switch (name) {
                case "asin" -> unary(this::asinh, "asinh({})");
                case "acos" -> unary(this::acosh, "acosh({})");
                case "atan" -> unary(this::atanh, "atanh({})");
                default -> {
                }
            }
            return;
        }
        switch (name) {
            case "asin" -> unary(v -> {
                if (v < -1 || v > 1) {
                    throw CalculatorException.invalidInput();
                }
                return fromRadians(Math.asin(v));
            }, "asin({})");
            case "acos" -> unary(v -> {
                if (v < -1 || v > 1) {
                    throw CalculatorException.invalidInput();
                }
                return fromRadians(Math.acos(v));
            }, "acos({})");
            case "atan" -> unary(v -> fromRadians(Math.atan(v)), "atan({})");
            default -> {
            }
        }
    }

    private String angleSuffix() {
        return switch (angleMode) {
            case DEG -> "°";
            case RAD -> "";
            case GRAD -> "ᵍ";
        };
    }

    private double toRadians(double value) {
        return switch (angleMode) {
            case DEG -> Math.toRadians(value);
            case RAD -> value;
            case GRAD -> value * Math.PI / 200.0;
        };
    }

    private double fromRadians(double radians) {
        return switch (angleMode) {
            case DEG -> Math.toDegrees(radians);
            case RAD -> radians;
            case GRAD -> radians * 200.0 / Math.PI;
        };
    }

    private double toDegreesFromMode(double value) {
        return switch (angleMode) {
            case DEG -> value;
            case RAD -> Math.toDegrees(value);
            case GRAD -> value * 0.9;
        };
    }

    private double reciprocal(double v) {
        if (v == 0.0) {
            throw CalculatorException.divideByZero();
        }
        return 1.0 / v;
    }

    private double sqrt(double v) {
        if (v < 0) {
            throw CalculatorException.invalidInput();
        }
        return Math.sqrt(v);
    }

    private double log10(double v) {
        if (v <= 0) {
            throw CalculatorException.invalidInput();
        }
        return Math.log10(v);
    }

    private double ln(double v) {
        if (v <= 0) {
            throw CalculatorException.invalidInput();
        }
        return Math.log(v);
    }

    private double factorial(double v) {
        if (v < 0 || v != Math.floor(v) || v > 170) {
            throw CalculatorException.invalidInput();
        }
        double result = 1;
        for (int i = 2; i <= (int) v; i++) {
            result *= i;
        }
        checkFinite(result);
        return result;
    }

    private double asinh(double v) {
        return Math.log(v + Math.sqrt(v * v + 1.0));
    }

    private double acosh(double v) {
        if (v < 1) {
            throw CalculatorException.invalidInput();
        }
        return Math.log(v + Math.sqrt(v * v - 1.0));
    }

    private double atanh(double v) {
        if (v <= -1 || v >= 1) {
            throw CalculatorException.invalidInput();
        }
        return 0.5 * Math.log((1 + v) / (1 - v));
    }

    private void startExponentEntry() {
        // "exp" button: multiply current by 10^n — Windows inserts e+0 for scientific entry
        if (!entering || overwrite) {
            entry = formatRaw(currentValue());
            entering = true;
            overwrite = false;
        }
        if (!entry.toLowerCase(Locale.ROOT).contains("e")) {
            entry = entry + "e+0";
        }
    }

    private void memoryClear() {
        memory = 0;
        memorySet = false;
    }

    private void memoryRecall() {
        if (!memorySet) {
            setCurrentValue(0, false);
        } else {
            setCurrentValue(memory, false);
        }
        entering = false;
        overwrite = true;
        if (afterEquals) {
            expression = "";
            afterEquals = false;
            expressionComplete = false;
        }
    }

    private void memoryStore() {
        memory = currentValue();
        memorySet = true;
        overwrite = true;
        entering = false;
    }

    private void memoryAdd() {
        memory += currentValue();
        memorySet = true;
        overwrite = true;
        entering = false;
    }

    private void memorySubtract() {
        memory -= currentValue();
        memorySet = true;
        overwrite = true;
        entering = false;
    }

    private void reduceWhile(BinaryOp incoming) {
        while (!ops.isEmpty()) {
            BinaryOp top = ops.peekLast();
            boolean shouldReduce = incoming.isRightAssociative()
                    ? top.getPrecedence() > incoming.getPrecedence()
                    : top.getPrecedence() >= incoming.getPrecedence();
            if (!shouldReduce) {
                break;
            }
            // Do not reduce across parenthesis barrier
            if (!parenDepthMarkers.isEmpty() && ops.size() <= parenDepthMarkers.peekLast()) {
                break;
            }
            reduceOnce();
        }
    }

    private void reduceAll() {
        while (!ops.isEmpty()) {
            reduceOnce();
        }
    }

    private void reduceOnce() {
        if (ops.isEmpty() || values.size() < 2) {
            if (!ops.isEmpty()) {
                ops.removeLast();
            }
            return;
        }
        BinaryOp op = ops.removeLast();
        double right = values.removeLast();
        double left = values.removeLast();
        double result = op.apply(left, right);
        checkFinite(result);
        values.addLast(result);
        lastBinaryOp = op;
        lastOperand = right;
        lastResult = result;
    }

    private void appendOperandToExpression(double v) {
        String text;
        if (pendingConstantSymbol != null && pendingConstantValue != null
                && Double.compare(v, pendingConstantValue) == 0) {
            text = pendingConstantSymbol;
            pendingConstantSymbol = null;
            pendingConstantValue = null;
        } else if ("π".equals(entry) || "e".equals(entry)) {
            text = entry;
        } else {
            text = formatNumber(v);
        }
        if (expressionComplete) {
            expression = text;
            expressionComplete = false;
        } else {
            expression = expression + text;
        }
    }

    private void trimTrailingOperatorFromExpression() {
        String t = expression.stripTrailing();
        for (BinaryOp op : BinaryOp.values()) {
            String sym = op.getSymbol();
            if (t.endsWith(sym)) {
                expression = t.substring(0, t.length() - sym.length()).stripTrailing() + " ";
                return;
            }
        }
    }

    private double currentValue() {
        if (pendingConstantValue != null && !entering && ("π".equals(entry) || "e".equals(entry) || overwrite)) {
            // fall through — prefer parsed entry
        }
        if ("π".equals(entry)) {
            return Math.PI;
        }
        if ("e".equals(entry)) {
            return Math.E;
        }
        try {
            return Double.parseDouble(entry.replace('−', '-'));
        } catch (NumberFormatException ex) {
            return lastResult;
        }
    }

    private void setCurrentValue(double value, boolean keepEntering) {
        checkFinite(value);
        entry = formatRaw(value);
        entering = keepEntering;
        pendingConstantSymbol = null;
        pendingConstantValue = null;
    }

    private void checkFinite(double value) {
        if (Double.isNaN(value)) {
            throw CalculatorException.invalidInput();
        }
        if (Double.isInfinite(value)) {
            throw CalculatorException.overflow();
        }
    }

    private void setError(String message) {
        error = true;
        errorMessage = message;
        entering = false;
        overwrite = true;
    }

    private void resetWorkingState() {
        entry = "0";
        entering = false;
        overwrite = true;
        error = false;
        errorMessage = null;
        values.clear();
        ops.clear();
        parenDepthMarkers.clear();
        openParens = 0;
        pendingConstantSymbol = null;
        pendingConstantValue = null;
        scientificNotation = scientificNotation; // keep FE mode
        // keep angle mode, hyp, 2nd, memory
    }

    private int digitCount(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private String formatDisplay(double value) {
        if (scientificNotation) {
            return String.format(Locale.US, "%.15e", value).replaceAll("\\.?0+e", "e").replaceAll("e\\+0+", "e+");
        }
        return formatNumber(value);
    }

    private String formatRaw(double value) {
        if (value == (long) value && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        // Snap floating-point noise (e.g. cos(90°) → ~1e-16) to zero, like Windows Calculator
        if (value == 0 || Math.abs(value) < 1e-12) {
            return "0";
        }
        double abs = Math.abs(value);
        if (abs >= 1e16 || abs < 1e-4) {
            return String.format(Locale.US, "%.15g", value);
        }
        // Trim trailing zeros
        String s = String.format(Locale.US, "%.15f", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (s.equals("-0")) {
            return "0";
        }
        return s;
    }

    // --- Package-visible helpers for tests ---

    AngleMode getAngleMode() {
        return angleMode;
    }

    double getMemory() {
        return memory;
    }

    boolean isError() {
        return error;
    }

    /**
     * Evaluate a simple infix expression string for unit tests (uses this engine's angle mode).
     * Supports + - * / ^ mod parentheses and functions via sequential command API preferred.
     */
    public synchronized double evaluateExpression(String infix) {
        clearAll();
        List<String> tokens = tokenize(infix);
        for (String token : tokens) {
            applyToken(token);
        }
        input("EQUALS");
        if (error) {
            throw new CalculatorException(errorMessage);
        }
        return currentValue();
    }

    private void applyToken(String token) {
        switch (token) {
            case "+" -> input("ADD");
            case "-", "−" -> input("SUB");
            case "*", "×", "x" -> input("MUL");
            case "/", "÷" -> input("DIV");
            case "^" -> input("POW");
            case "mod" -> input("MOD");
            case "(" -> input("LPAREN");
            case ")" -> input("RPAREN");
            case "pi", "π" -> input("PI");
            case "e" -> input("E");
            default -> {
                if (token.matches("-?\\d+(\\.\\d+)?")) {
                    for (char c : token.toCharArray()) {
                        if (c == '-') {
                            input("NEGATE");
                        } else if (c == '.') {
                            input("DECIMAL");
                        } else {
                            input("DIGIT_" + c);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Unknown token: " + token);
                }
            }
        }
    }

    private List<String> tokenize(String infix) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < infix.length(); i++) {
            char c = infix.charAt(i);
            if (Character.isWhitespace(c)) {
                flush(num, tokens);
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                num.append(c);
                continue;
            }
            flush(num, tokens);
            if (c == 'π') {
                tokens.add("π");
            } else if (infix.startsWith("mod", i)) {
                tokens.add("mod");
                i += 2;
            } else if (infix.startsWith("pi", i)) {
                tokens.add("pi");
                i += 1;
            } else {
                tokens.add(String.valueOf(c));
            }
        }
        flush(num, tokens);
        return tokens;
    }

    private void flush(StringBuilder num, List<String> tokens) {
        if (!num.isEmpty()) {
            tokens.add(num.toString());
            num.setLength(0);
        }
    }
}
