# Scientific Calculator

A web-based scientific calculator inspired by the **Scientific** mode of the Windows 11 Calculator app. Calculation logic runs in Java; the browser UI is plain HTML/CSS/JS.

## Features

- Expression line + main display
- Memory: MC, MR, M+, M−, MS
- Angle modes: DEG / RAD / GRAD
- F-E (scientific notation) and 2nd / HYP toggles
- Trig: sin, cos, tan (inverses via 2nd; hyperbolic via HYP)
- Powers & roots: x², x³, xʸ, √, ∛, ʸ√x, 10ˣ, 2ˣ, eˣ
- log, ln, π, e, n!, 1/x, |x|, exp, mod, %, parentheses, +/−
- CE, C, backspace, decimal point
- Keyboard input (digits, operators, Enter, Esc, F3–F5, and common shortcuts)
- Light / dark theme toggle
- Operator precedence with parentheses; Windows-style errors (`Cannot divide by zero`, `Result is undefined`, `Invalid input`)

## Stack

- Java 21 (LTS)
- Spring Boot 4 (Maven)
- JUnit 5 for the calculation engine

## Requirements

- JDK 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)

## Build and run

```bash
# Run tests
./mvnw test

# Start the app (http://localhost:3847)
./mvnw spring-boot:run
```

Or build a jar and run it:

```bash
./mvnw -DskipTests package
java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar
```

Open [http://localhost:3847](http://localhost:3847) in your browser.

## Project layout

```
src/main/java/com/calculator/scientific/
  engine/          # Pure Java calculator engine (precedence, trig, memory, errors)
  web/             # REST API binding engine state to an HTTP session
src/main/resources/static/
  index.html       # Windows-style Scientific UI
  css/ calculator.css
  js/  calculator.js
src/test/java/.../engine/
  CalculatorEngineTest.java
  BinaryOpTest.java
```

The UI posts button/key commands to `POST /api/calculator/input` with JSON `{ "command": "SIN" }`. The server returns the updated display snapshot.
