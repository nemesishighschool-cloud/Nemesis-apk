/* DOM regressions for locating and clicking DeepSeek continuation controls. */
const fs = require("fs");
const path = require("path");
const src = fs.readFileSync(path.join(__dirname, "..", "app", "src", "main", "assets", "js", "adapter-deepseek.js"), "utf8");

function exercise({ label, attrs = {}, className = "", initiallyDisabled = false, hidden = false }) {
  let clicks = 0;
  let isDisabled = initiallyDisabled;
  const button = {
    innerText: label,
    textContent: label,
    tagName: "BUTTON",
    className,
    disabled: false,
    getAttribute(name) { return attrs[name] || null; },
    getBoundingClientRect() { return hidden ? { width: 0, height: 0 } : { left: 10, top: 20, width: 88, height: 32 }; },
    scrollIntoView() {},
    click() { clicks++; },
  };
  const win = { devicePixelRatio: 2, getComputedStyle() { return { display: "block", visibility: "visible", opacity: "1" }; } };
  const doc = { querySelectorAll(selector) {
    return selector.includes("button") || selector.includes("continue") ? [button] : [];
  } };
  new Function("window", "document", "location", src)(win, doc, { href: "https://chat.deepseek.com/" });
  const adapter = win.__napDS;
  let state = JSON.parse(adapter.continueAvailable());
  if (hidden) {
    if (state.available || JSON.parse(adapter.continueGeneration()).ok || clicks) throw new Error("hidden continuation control was accepted");
    return;
  }
  if (!state.available) throw new Error(`continuation control was missed: ${label}`);
  if (isDisabled || attrs["aria-disabled"] === "true") {
    if (state.enabled || JSON.parse(adapter.continueGeneration()).ok || clicks) throw new Error("disabled Continue button was clicked");
    isDisabled = false;
    attrs["aria-disabled"] = "false";
    button.disabled = false;
    state = JSON.parse(adapter.continueAvailable());
  }
  if (!state.enabled) throw new Error(`enabled continuation control was not recognized: ${label}`);
  const target = JSON.parse(adapter.continueTarget());
  if (!target.ok || target.x !== 54 || target.y !== 36 || target.ratio !== 2) throw new Error('invalid native tap target');
  if (!JSON.parse(adapter.continueGeneration()).ok || clicks !== 1) throw new Error(`continuation control was not clicked exactly once: ${label}`);
}

exercise({ label: "Continue generating", attrs: { "aria-disabled": "true" } });
exercise({ label: "", attrs: { "aria-label": "Continuer la génération" } });
exercise({ label: "", attrs: { "data-testid": "continue-button" } });
exercise({ label: "", className: "continue-action" });
exercise({ label: "Continue", hidden: true });
console.log("PASS: English, French, test-id and class continuation controls are detected; disabled/hidden controls are never clicked.");
