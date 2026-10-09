package com.calculator.scientific.web;

import com.calculator.scientific.engine.CalculatorEngine;
import com.calculator.scientific.engine.CalculatorSnapshot;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/calculator")
public class CalculatorController {

    private static final String SESSION_KEY = "calculatorEngine";

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CalculatorSnapshot state(HttpSession session) {
        return engine(session).snapshot();
    }

    @PostMapping(path = "/input", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CalculatorSnapshot input(HttpSession session, @RequestBody Map<String, String> body) {
        String command = body.getOrDefault("command", "");
        return engine(session).input(command);
    }

    @PostMapping(path = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    public CalculatorSnapshot reset(HttpSession session) {
        return engine(session).clearAll();
    }

    private CalculatorEngine engine(HttpSession session) {
        CalculatorEngine engine = (CalculatorEngine) session.getAttribute(SESSION_KEY);
        if (engine == null) {
            engine = new CalculatorEngine();
            session.setAttribute(SESSION_KEY, engine);
        }
        return engine;
    }
}
