/* Reset-password page.
 *
 * Deliberately separate from auth.js: this page is a plain form post, so the
 * script only handles the eye toggles, the "!" policy overlay and the live
 * server check. As in auth.js the browser decides nothing — every verdict here
 * comes from POST /api/password-check.
 */
(function () {
  "use strict";

  var form = document.getElementById("resetForm");
  if (!form) return;

  var passwordInput = document.getElementById("password");
  var confirmInput = document.getElementById("confirm_password");
  var policyBtn = document.getElementById("policyInfoBtn");
  var popover = document.getElementById("policyPopover");
  var popMessage = document.getElementById("policyPopMessage");
  var stateText = document.getElementById("passwordState");
  var checklist = popover ? popover.querySelectorAll("[data-rule]") : [];
  var endpoint = form.getAttribute("data-check-endpoint");
  var email = form.getAttribute("data-email") || "";
  var tokenField = form.querySelector("input[name=csrf_token]");

  var pinned = false;
  var inFlight = false;
  var queued = false;
  var seq = 0;

  /* ---------- show / hide ---------- */
  function bindVisibility(button, input) {
    if (!button || !input) return;
    button.addEventListener("click", function () {
      var showing = input.type === "text";
      input.type = showing ? "password" : "text";
      button.classList.toggle("is-visible", !showing);
      button.setAttribute("aria-pressed", String(!showing));
      button.setAttribute("aria-label", showing ? "Show password" : "Hide password");
      input.focus({ preventScroll: true });
    });
  }

  bindVisibility(document.getElementById("togglePassword"), passwordInput);
  bindVisibility(document.getElementById("toggleConfirm"), confirmInput);

  /* ---------- policy overlay ---------- */
  function openPopover() {
    if (!popover) return;
    popover.hidden = false;
    if (policyBtn) policyBtn.setAttribute("aria-expanded", "true");
    if (passwordInput && passwordInput.value) {
      lastSent = null;
      check();
    }
  }

  function closePopover() {
    if (!popover) return;
    popover.hidden = true;
    pinned = false;
    if (policyBtn) policyBtn.setAttribute("aria-expanded", "false");
  }

  if (policyBtn && popover) {
    policyBtn.addEventListener("mouseenter", openPopover);
    policyBtn.addEventListener("click", function (event) {
      event.preventDefault();
      if (popover.hidden) {
        pinned = true;
        openPopover();
      } else if (pinned) {
        closePopover();
      } else {
        pinned = true;
      }
    });
    policyBtn.addEventListener("mouseleave", function () {
      if (pinned) return;
      window.setTimeout(function () {
        if (!pinned) closePopover();
      }, 180);
    });
    popover.addEventListener("mouseenter", function () {
      pinned = true;
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && !popover.hidden) {
        closePopover();
        policyBtn.focus();
      }
    });
    document.addEventListener("click", function (event) {
      if (popover.hidden) return;
      if (popover.contains(event.target) || policyBtn.contains(event.target)) return;
      closePopover();
    });
  }

  /* ---------- live server check ---------- */
  function paint(checks) {
    Array.prototype.forEach.call(checklist, function (item) {
      var key = item.getAttribute("data-rule");
      var met = Boolean(checks && checks[key]);
      item.classList.toggle("is-met", met);
      item.classList.toggle("is-unmet", Boolean(checks) && !met);
    });
  }

  function setState(state, message) {
    if (policyBtn) policyBtn.setAttribute("data-state", state);
    if (stateText) stateText.textContent = message || "";
    if (popMessage) {
      popMessage.classList.toggle("is-valid", state === "valid");
      popMessage.classList.toggle("is-invalid", state === "invalid");
    }
  }

  var lastSent = null;

  function check() {
    if (!endpoint || !passwordInput) return;
    var password = passwordInput.value;
    var current = ++seq;

    if (!password) {
      lastSent = "";
      paint(null);
      setState("empty", "");
      if (popMessage) {
        popMessage.textContent =
          "Enforced by the server. The tick turns green when your password passes.";
      }
      return;
    }
    if (inFlight) {
      queued = true;
      return;
    }

    inFlight = true;
    lastSent = password;
    setState("pending", "Checking password…");

    var payload = new FormData();
    payload.append("password", password);
    payload.append("email", email);
    payload.append("csrf_token", tokenField ? tokenField.value : "");

    fetch(endpoint, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "X-Requested-With": "fetch",
        "X-CSRF-Token": tokenField ? tokenField.value : ""
      },
      body: payload,
      credentials: "same-origin",
      cache: "no-store"
    })
      .then(function (response) {
        return response.json().catch(function () { return null; });
      })
      .then(function (data) {
        inFlight = false;
        if (queued) {
          queued = false;
          check();
          return;
        }
        if (current !== seq || !data) return;
        if (data.csrfToken && tokenField) tokenField.value = data.csrfToken;
        if (data.empty) {
          paint(null);
          setState("empty", "");
          return;
        }
        paint(data.checks);
        if (data.valid) {
          setState("valid", "Password meets the policy.");
          if (popMessage) {
            popMessage.textContent = "This password meets the Alpha Group policy — you can continue.";
          }
        } else {
          var reason = (data.violations && data.violations[0]) ||
            "Password does not meet the policy yet.";
          setState("invalid", reason);
          if (popMessage) popMessage.textContent = reason;
        }
      })
      .catch(function () {
        inFlight = false;
        setState("empty", "");
      });
  }

  if (passwordInput) {
    passwordInput.addEventListener("input", function () {
      if (passwordInput.value === lastSent && !queued) return;
      check();
    });
    passwordInput.addEventListener("blur", check);
    if (passwordInput.value) check();
  }
})();
