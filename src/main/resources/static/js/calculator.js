(() => {
  const displayEl = document.getElementById("display");
  const expressionEl = document.getElementById("expression");
  const btnMC = document.getElementById("btnMC");
  const btnMR = document.getElementById("btnMR");
  const btnFE = document.getElementById("btnFE");
  const btn2ND = document.getElementById("btn2ND");
  const btnHYP = document.getElementById("btnHYP");
  const themeToggle = document.getElementById("themeToggle");

  let state = {
    display: "0",
    expression: "",
    angleMode: "DEG",
    scientificNotation: false,
    secondFunction: false,
    hyperbolic: false,
    memoryHasValue: false,
    error: false,
  };

  const invLabels = {
    sin: "sin⁻¹",
    cos: "cos⁻¹",
    tan: "tan⁻¹",
    sinh: "sinh⁻¹",
    cosh: "cosh⁻¹",
    tanh: "tanh⁻¹",
  };

  async function api(path, options) {
    const res = await fetch(path, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      ...options,
    });
    if (!res.ok) {
      throw new Error("Request failed");
    }
    return res.json();
  }

  function applyState(next) {
    state = next;
    displayEl.textContent = next.display;
    displayEl.classList.toggle("error", !!next.error);
    expressionEl.textContent = next.expression || "";

    btnMC.disabled = !next.memoryHasValue;
    btnMR.disabled = !next.memoryHasValue;

    ["DEG", "RAD", "GRAD"].forEach((mode) => {
      const el = document.getElementById("btn" + mode);
      if (el) el.classList.toggle("active", next.angleMode === mode);
    });

    btnFE.setAttribute("aria-pressed", String(!!next.scientificNotation));
    btn2ND.setAttribute("aria-pressed", String(!!next.secondFunction));
    btnHYP.setAttribute("aria-pressed", String(!!next.hyperbolic));

    updateDualButtons();
    updateTrigButtons();
  }

  function updateDualButtons() {
    document.querySelectorAll(".dual").forEach((btn) => {
      const second = state.secondFunction;
      const label = second ? btn.dataset.label2 : btn.dataset.label;
      btn.innerHTML = label;
      btn.dataset.cmd = second ? btn.dataset.second : btn.dataset.primary;
    });
  }

  function updateTrigButtons() {
    document.querySelectorAll(".trig").forEach((btn) => {
      const base = btn.dataset.label; // sin, cos, tan
      let name = base;
      if (state.hyperbolic) {
        name = base + "h";
      }
      if (state.secondFunction) {
        btn.textContent = invLabels[name] || name + "⁻¹";
        const inv = "A" + name.toUpperCase();
        btn.dataset.cmd = inv;
      } else {
        btn.textContent = name;
        btn.dataset.cmd = name.toUpperCase();
      }
    });
  }

  async function send(command) {
    if (!command) return;
    try {
      const next = await api("/api/calculator/input", {
        method: "POST",
        body: JSON.stringify({ command }),
      });
      applyState(next);
    } catch (err) {
      displayEl.textContent = "Error";
      displayEl.classList.add("error");
    }
  }

  document.getElementById("pad").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cmd]");
    if (!btn) return;
    send(btn.dataset.cmd);
  });

  document.querySelector(".memory-row").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cmd]");
    if (!btn || btn.disabled) return;
    send(btn.dataset.cmd);
  });

  document.querySelector(".mode-row").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cmd]");
    if (!btn) return;
    send(btn.dataset.cmd);
  });

  themeToggle.addEventListener("click", () => {
    const html = document.documentElement;
    const next = html.getAttribute("data-theme") === "dark" ? "light" : "dark";
    if (next === "dark") {
      html.setAttribute("data-theme", "dark");
    } else {
      html.removeAttribute("data-theme");
    }
    localStorage.setItem("calc-theme", next);
  });

  const savedTheme = localStorage.getItem("calc-theme");
  if (savedTheme === "dark" || (!savedTheme && window.matchMedia("(prefers-color-scheme: dark)").matches)) {
    document.documentElement.setAttribute("data-theme", "dark");
  }

  function onKeyDown(e) {
    if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA")) {
      return;
    }

    const key = e.key;
    let cmd = null;

    if (key >= "0" && key <= "9") cmd = "DIGIT_" + key;
    else if (key === ".") cmd = "DECIMAL";
    else if (key === "+") cmd = "ADD";
    else if (key === "-") cmd = "SUB";
    else if (key === "*" || key === "x" || key === "X") cmd = "MUL";
    else if (key === "/") cmd = "DIV";
    else if (key === "%") cmd = "PERCENT";
    else if (key === "^") cmd = "POW";
    else if (key === "(") cmd = "LPAREN";
    else if (key === ")") cmd = "RPAREN";
    else if (key === "Enter" || key === "=") cmd = "EQUALS";
    else if (key === "Backspace") cmd = "BACKSPACE";
    else if (key === "Escape") cmd = "C";
    else if (key === "Delete") cmd = "CE";
    else if (key === "F3") cmd = "DEG";
    else if (key === "F4") cmd = "RAD";
    else if (key === "F5") cmd = "GRAD";
    else if (key === "p" || key === "P") cmd = "PI";
    else if (!e.ctrlKey && !e.metaKey && (key === "s" || key === "S")) {
      cmd = e.shiftKey ? "ASIN" : "SIN";
    } else if (!e.ctrlKey && !e.metaKey && (key === "o" || key === "O")) {
      cmd = e.shiftKey ? "ACOS" : "COS";
    } else if (!e.ctrlKey && !e.metaKey && (key === "t" || key === "T")) {
      cmd = e.shiftKey ? "ATAN" : "TAN";
    } else if (key === "l" || key === "L") cmd = "LOG";
    else if (key === "n" || key === "N") cmd = e.ctrlKey ? "EXP" : "LN";
    else if (key === "q" || key === "Q") cmd = "SQUARE";
    else if (key === "r" || key === "R") cmd = "SQRT";
    else if (key === "!") cmd = "FACTORIAL";

    if (cmd) {
      e.preventDefault();
      send(cmd);
    }
  }

  window.addEventListener("keydown", onKeyDown);

  // Initial labels + state
  updateDualButtons();
  updateTrigButtons();
  api("/api/calculator")
    .then(applyState)
    .catch(() => {
      displayEl.textContent = "Unavailable";
      displayEl.classList.add("error");
    });
})();
