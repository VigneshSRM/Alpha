/* ==========================================================================
   Alpha Group of India — portal interactions

   IMPORTANT: this file performs NO validation of its own. It improves the feel
   of the form (password visibility, caps-lock hint, spinner) and paints the
   verdicts it is given. The "!" indicator asks the server (/api/password-check)
   while you type, so the tick can never disagree with the submit decision.
   ========================================================================== */
(function () {
  "use strict";

  function $(id) { return document.getElementById(id); }

  function fieldOf(input) { return input ? input.closest("[data-field]") : null; }

  function setFieldError(input, message) {
    var field = fieldOf(input);
    if (!field) return;
    var error = field.querySelector(".field__error");
    if (error) error.textContent = message || "";
    field.classList.toggle("is-invalid", Boolean(message));
    field.classList.remove("is-valid");
    input.setAttribute("aria-invalid", message ? "true" : "false");
  }

  function clearFieldError(input) {
    if (!input) return;
    var field = fieldOf(input);
    if (!field) return;
    var error = field.querySelector(".field__error");
    if (error) error.textContent = "";
    field.classList.remove("is-invalid");
    input.removeAttribute("aria-invalid");
  }

  /* ------------------------------------------------------------------ */
  /* Flash messages                                                     */
  /* ------------------------------------------------------------------ */
  function flashBox(kind) {
    var el = document.querySelector("[data-flash]");
    if (el && kind !== "success") return el;
    return document.querySelector("[data-success]");
  }

  function showFlash(message, kind) {
    var box;
    if (kind === "success") {
      box = document.querySelector("[data-success]");
    } else if (kind === "notice") {
      // Reuse the error box styled as a notice if no notice slot exists.
      box = document.querySelector("[data-flash]");
      if (box) box.classList.remove("flash--error");
      if (box) box.classList.add("flash--notice");
    } else {
      box = document.querySelector("[data-flash]");
      if (box) {
        box.classList.remove("flash--notice");
        box.classList.add("flash--error");
      }
    }
    if (!box) return;
    var text = box.querySelector("[data-flash-text], [data-success-text]");
    if (text) text.textContent = message;
    box.hidden = false;
    try {
      box.scrollIntoView({ block: "nearest", behavior: "smooth" });
    } catch (error) {
      /* older engines */
    }
  }

  function hideFlash() {
    var box = document.querySelector("[data-flash]");
    if (box) {
      box.hidden = true;
      var text = box.querySelector("[data-flash-text]");
      if (text) text.textContent = "";
    }
  }

  function hideSuccess() {
    var box = document.querySelector("[data-success]");
    if (box) box.hidden = true;
  }

  /* ------------------------------------------------------------------ */
  /* Password visibility                                                */
  /* ------------------------------------------------------------------ */
  function setRevealState(button, reveal) {
    if (!button) return;
    button.classList.toggle("is-revealed", Boolean(reveal));
    button.setAttribute("aria-pressed", reveal ? "true" : "false");
    button.setAttribute("aria-label", reveal ? "Hide password" : "Show password");
    button.title = reveal ? "Hide password" : "Show password";
  }

  function bindVisibility(button, input) {
    if (!button || !input) return;
    setRevealState(button, input.type !== "password");
    button.addEventListener("click", function () {
      var reveal = input.type === "password";
      input.type = reveal ? "text" : "password";
      setRevealState(button, reveal);
      input.focus({ preventScroll: true });
      var end = input.value.length;
      try { input.setSelectionRange(end, end); } catch (error) { /* not a text input */ }
    });
  }

  function bindCapsLock(input, hint) {
    if (!input || !hint) return;
    function update(event) {
      var on = typeof event.getModifierState === "function" &&
        event.getModifierState("CapsLock");
      hint.hidden = !on;
    }
    input.addEventListener("keydown", update);
    input.addEventListener("keyup", update);
    input.addEventListener("blur", function () { hint.hidden = true; });
  }

  /* ------------------------------------------------------------------ */
  /* Modal plumbing                                                     */
  /* ------------------------------------------------------------------ */
  function createModal(modal) {
    if (!modal) return null;
    var lastFocused = null;

    function focusables() {
      return Array.prototype.slice
        .call(modal.querySelectorAll("button, [href], input, select, textarea"))
        .filter(function (node) { return !node.hidden && node.offsetParent !== null; });
    }

    function open() {
      lastFocused = document.activeElement;
      modal.hidden = false;
      document.body.style.overflow = "hidden";
      var nodes = focusables();
      if (nodes.length) nodes[0].focus({ preventScroll: true });
    }

    function close() {
      modal.hidden = true;
      document.body.style.overflow = "";
      if (lastFocused && typeof lastFocused.focus === "function") {
        lastFocused.focus({ preventScroll: true });
      }
    }

    modal.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        event.preventDefault();
        close();
        return;
      }
      if (event.key !== "Tab") return;
      var nodes = focusables();
      if (!nodes.length) return;
      var first = nodes[0];
      var last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });

    return { open: open, close: close, element: modal };
  }

  /* ------------------------------------------------------------------ */
  /* Submit helper                                                      */
  /* ------------------------------------------------------------------ */
  function postJson(url, formData, csrfToken) {
    return fetch(url, {
      method: "POST",
      headers: {
        "Accept": "application/json",
        "X-Requested-With": "fetch",
        "X-CSRF-Token": csrfToken || ""
      },
      body: formData,
      credentials: "same-origin"
    }).then(function (response) {
      return response
        .json()
        .catch(function () { return { message: "Unexpected response from the server." }; })
        .then(function (data) { return { status: response.status, data: data }; });
    });
  }

  function setLoading(button, state, busyLabel) {
    if (!button) return;
    button.disabled = state;
    button.classList.toggle("is-loading", state);
    var label = button.querySelector(".btn__label");
    if (!label) return;
    if (state) {
      if (!label.dataset.idle) label.dataset.idle = label.textContent;
      if (busyLabel) label.textContent = busyLabel;
    } else if (label.dataset.idle) {
      label.textContent = label.dataset.idle;
    }
  }


  /* ------------------------------------------------------------------ */
  /* Mobile number                                                       */
  /*                                                                     */
  /* Cosmetic only: digits are kept, the hint follows the country, and a  */
  /* length check gives instant feedback. The server re-does all of it   */
  /* against the same table and its verdict is the one that stands.      */
  /* ------------------------------------------------------------------ */
  function digitsFor(select) {
    var option = select && select.options[select.selectedIndex];
    var raw = option ? option.getAttribute("data-digits") || "" : "";
    var parts = raw.split("-");
    var min = parseInt(parts[0], 10);
    var max = parseInt(parts[1] || parts[0], 10);
    return {
      min: isNaN(min) ? 6 : min,
      max: isNaN(max) ? 15 : max,
      name: option ? option.textContent.replace(/^\+\d+\s*/, "").trim() : ""
    };
  }

  function initPhone() {
    var input = $("phone");
    var select = $("country_code");
    var hint = $("phoneHint");
    var form = $("authForm");
    if (!input || !select || !form) return;

    function describe(silent) {
      var plan = digitsFor(select);
      if (hint) {
        hint.textContent = (plan.min === plan.max
          ? plan.min + " digits"
          : plan.min + " to " + plan.max + " digits") + " for " + plan.name;
      }
      var count = input.value.replace(/\D/g, "").length;
      var short = count > 0 && count < plan.min;
      var long = count > plan.max;
      fieldOf(input).classList.toggle("is-invalid", Boolean(short || long));
      if (silent) return;
      if (long) {
        setFieldError(input, "That is longer than the " + plan.max + " digits " + plan.name + " uses.");
      } else if (short && count >= plan.min - 3) {
        setFieldError(input, "Keep going — " + plan.name + " numbers have "
          + (plan.min === plan.max ? plan.min : plan.min + " to " + plan.max) + " digits.");
      } else {
        clearFieldError(input);
      }
    }

    /* Delegated so it runs after the generic "typing clears the verdict"
       handler above; otherwise that handler would wipe the live message. */
    form.addEventListener("input", function (event) {
      if (event.target !== input) return;
      var cleaned = input.value.replace(/[^\d\s()+-]/g, "");
      if (cleaned !== input.value) input.value = cleaned;
      describe(false);
    });

    select.addEventListener("change", function () {
      describe(true);
      input.focus();
    });

    describe(true);
  }

  /* ------------------------------------------------------------------ */
  /* Boot                                                               */
  /* ------------------------------------------------------------------ */
  function init() {
    var form = $("authForm");
    if (!form) return;

    var mode = form.dataset.mode || "login";
    var emailInput = $("email");
    var nameInput = $("name");
    var passwordInput = $("password");
    var confirmInput = $("confirm_password");
    var phoneInput = $("phone");
    var submitBtn = $("submitBtn");
    var tokenField = form.querySelector("input[name=csrf_token]");
    var fieldMap = {
      email: emailInput,
      name: nameInput,
      password: passwordInput,
      confirm_password: confirmInput,
      phone: phoneInput
    };

    /* --- password visibility + caps lock --------------------------- */
    bindVisibility($("togglePassword"), passwordInput);
    bindVisibility($("toggleConfirm"), confirmInput);
    bindCapsLock(passwordInput, $("capsLock"));

    /* --- clear e-mail ---------------------------------------------- */
    var clearBtn = $("clearEmail");
    if (clearBtn && emailInput) {
      clearBtn.hidden = emailInput.value.length === 0;
      clearBtn.addEventListener("click", function () {
        emailInput.value = "";
        clearBtn.hidden = true;
        clearFieldError(emailInput);
        emailInput.focus();
      });
      emailInput.addEventListener("input", function () {
        clearBtn.hidden = emailInput.value.length === 0;
      });
    }

    /* --- password requirements: "!" popover + live state ----------- */
    var policyBtn = $("policyInfoBtn");
    var popover = $("policyPopover");
    var popMessage = $("policyPopMessage");
    var stateText = $("passwordState");
    var checklist = popover ? popover.querySelectorAll("[data-rule]") : [];
    var checkEndpoint = form.getAttribute("data-check-endpoint");
    var checkSeq = 0;

    // Hover opens it on a mouse; a click/tap "pins" it so the synthesized
    // mouseleave that touch devices emit cannot close it straight away.
    var pinned = false;

    function openPopover() {
      if (!popover) return;
      popover.hidden = false;
      if (policyBtn) policyBtn.setAttribute("aria-expanded", "true");
      // Always show fresh ticks — never a stale verdict.
      if (passwordInput && passwordInput.value) forceCheck();
    }

    function closePopover() {
      if (!popover) return;
      popover.hidden = true;
      pinned = false;
      if (policyBtn) policyBtn.setAttribute("aria-expanded", "false");
    }

    function paintChecklist(checks) {
      Array.prototype.forEach.call(checklist, function (item) {
        var key = item.getAttribute("data-rule");
        var met = Boolean(checks && checks[key]);
        item.classList.toggle("is-met", met);
        item.classList.toggle("is-unmet", Boolean(checks) && !met);
      });
    }

    function setPasswordState(state, message) {
      if (policyBtn) policyBtn.setAttribute("data-state", state);
      if (stateText) stateText.textContent = message || "";
      if (popMessage) {
        popMessage.classList.toggle("is-valid", state === "valid");
        popMessage.classList.toggle("is-invalid", state === "invalid");
      }
    }

    var lastSent = null;
    var inFlight = false;
    var queued = false;

    function resetIndicator() {
      paintChecklist(null);
      setPasswordState("empty", "");
      if (popMessage) {
        popMessage.textContent =
          "Enforced by the authentication server. The tick turns green when your password passes.";
      }
    }

    /**
     * Ask the server whether this password satisfies the policy.
     *
     * Runs immediately on input (no setTimeout: browsers throttle timers in
     * background tabs, which made the tick appear late or not at all). At most
     * one request is in flight; the newest value is sent as soon as it lands.
     */
    function askServer(retry) {
      if (!checkEndpoint || !passwordInput) return;

      var password = passwordInput.value;
      var seq = ++checkSeq;

      if (!password) {
        lastSent = "";
        queued = false;
        resetIndicator();
        return;
      }

      if (inFlight) {
        queued = true;
        return;
      }

      inFlight = true;
      lastSent = password;
      setPasswordState("pending", "Checking password…");

      var payload = new FormData();
      payload.append("password", password);
      payload.append("email", emailInput ? emailInput.value : "");
      payload.append("name", nameInput ? nameInput.value : "");
      payload.append("csrf_token", tokenField ? tokenField.value : "");

      function finish() {
        inFlight = false;
        if (queued) {
          queued = false;
          askServer();
        }
      }

      fetch(checkEndpoint, {
        method: "POST",
        headers: {
          "Accept": "application/json",
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
          if (seq !== checkSeq) {
            finish();
            return;
          }
          if (!data) {
            // Non-JSON answer (proxy/error page): retry once, then stay neutral.
            finish();
            if (!retry) askServer(true);
            else resetIndicator();
            return;
          }
          if (data.code === "csrf" && data.csrfToken && tokenField) {
            tokenField.value = data.csrfToken;
            finish();
            if (!retry) askServer(true);
            return;
          }
          if (data.empty) {
            finish();
            resetIndicator();
            return;
          }

          paintChecklist(data.checks);
          if (data.valid) {
            setPasswordState("valid", "Password meets the policy.");
            if (popMessage) {
              popMessage.textContent =
                "This password meets the Alpha Group policy — you can continue.";
            }
          } else {
            var reason =
              (data.violations && data.violations[0]) ||
              "Password does not meet the policy yet.";
            setPasswordState("invalid", reason);
            if (popMessage) popMessage.textContent = reason;
          }
          finish();
        })
        .catch(function () {
          finish();
          if (!retry) askServer(true);
          else resetIndicator();
        });
    }

    /* Immediate + trailing-edge check: no timers involved. */
    function onPasswordInput() {
      if (passwordInput.value === lastSent && !queued) return;
      askServer();
    }

    /* Ask again even if the value has not changed (focus, popover open). */
    function forceCheck() {
      lastSent = null;
      askServer();
    }

    if (policyBtn && popover) {
      policyBtn.addEventListener("mouseenter", openPopover);
      // No "focus" listener: on touch devices focus fires just before click,
      // which made the popover open and immediately close. Keyboard users
      // still get it because Enter/Space trigger "click".
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

      // Keep it open while the pointer is over the popover itself.
      var closeTimer = null;
      function scheduleClose() {
        if (closeTimer) window.clearTimeout(closeTimer);
        closeTimer = window.setTimeout(function () {
          if (pinned) return;
          if (popover.contains(document.activeElement)) return;
          closePopover();
        }, 180);
      }
      policyBtn.addEventListener("mouseleave", scheduleClose);
      popover.addEventListener("mouseenter", function () {
        if (closeTimer) window.clearTimeout(closeTimer);
      });
      popover.addEventListener("mouseleave", scheduleClose);
      // Escape works wherever focus is (click-pinned popovers keep focus on
      // the "!" button, not inside the popover).
      document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && popover && !popover.hidden) {
          closePopover();
          if (policyBtn) policyBtn.focus();
        }
      });
      document.addEventListener("click", function (event) {
        if (popover.hidden) return;
        if (popover.contains(event.target) || policyBtn.contains(event.target)) return;
        closePopover();
      });
    }

    if (passwordInput) {
      passwordInput.addEventListener("input", onPasswordInput);
      passwordInput.addEventListener("change", onPasswordInput);
      passwordInput.addEventListener("blur", function () { forceCheck(); });
      if (passwordInput.value) askServer();
      else resetIndicator();
    }

    /* --- strong-password suggestion (signup popover) ---------------- */
    Array.prototype.forEach.call(
      document.querySelectorAll("[data-suggest-password]"),
      function (node) {
        node.addEventListener("click", function () {
          if (!window.AlphaPassword || !passwordInput) return;
          var generated = window.AlphaPassword.generate(16);
          passwordInput.value = generated;
          passwordInput.type = "text";
          setRevealState($("togglePassword"), true);
          if (confirmInput) {
            confirmInput.value = generated;
            clearFieldError(confirmInput);
          }
          clearFieldError(passwordInput);
          passwordInput.focus({ preventScroll: true });
          try {
            passwordInput.setSelectionRange(generated.length, generated.length);
          } catch (error) { /* noop */ }
          askServer();
        });
      }
    );

    /* --- forgot password ------------------------------------------- */
    var forgotModalEl = $("forgotModal");
    var forgotModal = createModal(forgotModalEl);
    var forgotBtn = $("forgotBtn");
    if (forgotBtn && forgotModal) {
      forgotBtn.addEventListener("click", function () {
        var target = $("forgotEmail");
        var status = $("forgotStatus");
        if (status) status.hidden = true;
        if (target) {
          if (emailInput && emailInput.value.trim()) target.value = emailInput.value.trim();
          clearFieldError(target);
        }
        forgotModal.open();
      });
    }

    Array.prototype.forEach.call(
      document.querySelectorAll("[data-close-forgot]"),
      function (node) {
        node.addEventListener("click", function () { if (forgotModal) forgotModal.close(); });
      }
    );

    var forgotForm = $("forgotForm");
    if (forgotForm) {
      forgotForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var emailField = $("forgotEmail");
        var errorBox = $("forgotEmailError");
        var status = $("forgotStatus");
        var button = $("forgotSubmit");
        var token = forgotForm.querySelector("input[name=csrf_token]");

        setLoading(button, true, "Sending…");
        if (errorBox) errorBox.textContent = "";
        if (status) status.hidden = true;

        postJson(
          forgotForm.getAttribute("action"),
          new FormData(forgotForm),
          token ? token.value : ""
        )
          .then(function (result) {
            setLoading(button, false);
            var data = result.data || {};
            if (data.ok) {
              if (status) {
                status.textContent = data.message || "If an account exists, a reset link is on its way.";
                status.hidden = false;
              }
              if (emailField) emailField.value = "";
              return;
            }
            var message = data.message || "Could not send the reset link. Please try again.";
            if (errorBox && (data.errors && data.errors.email || !data.errors)) {
              if (errorBox) errorBox.textContent = message;
            }
            if (data.code === "rate_limited" && status) {
              status.textContent = message;
              status.hidden = false;
            }
          })
          .catch(function () {
            setLoading(button, false);
            if (errorBox) errorBox.textContent = "Network error. Please check your connection.";
          });
      });
    }

    /* --- main submit ----------------------------------------------- */
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      hideFlash();
      hideSuccess();
      var busy = mode === "signup" ? "Creating account…" : "Signing in…";
      setLoading(submitBtn, true, busy);

      postJson(
        form.getAttribute("action"),
        new FormData(form),
        tokenField ? tokenField.value : ""
      )
        .then(function (result) {
          var data = result.data || {};

          // Signup with "confirm e-mail" enabled: no session yet.
          if (data.ok && data.code === "confirm_email") {
            setLoading(submitBtn, false);
            showFlash(data.message || "Check your inbox to confirm your e-mail.", "notice");
            window.setTimeout(function () {
              window.location.assign(data.redirect || "/login");
            }, 1600);
            return;
          }

          if (data.ok) {
            setLoading(submitBtn, false);
            showFlash(data.message || "Success. Redirecting…", "success");
            if (passwordInput) passwordInput.blur();
            window.setTimeout(function () {
              window.location.assign(data.redirect || "/dashboard");
            }, 550);
            return;
          }

          setLoading(submitBtn, false);

          // A rotated CSRF token is handed back so a retry just works.
          if (data.code === "csrf" && data.csrfToken && tokenField) {
            tokenField.value = data.csrfToken;
          }

          // Paint the backend's field-level verdicts.
          var errors = data.errors || {};
          var focused = false;
          Object.keys(errors).forEach(function (key) {
            var input = fieldMap[key];
            if (input) {
              setFieldError(input, errors[key]);
              if (!focused) {
                input.focus({ preventScroll: true });
                focused = true;
              }
            }
          });

          showFlash(data.message || "Please check the details and try again.", "error");
        })
        .catch(function () {
          setLoading(submitBtn, false);
          showFlash("Network error. Please check your connection and try again.", "error");
        });
    });

    /* Typing clears the last verdict for that field (no validation here). */
    form.addEventListener("input", function (event) {
      var target = event.target;
      if (!target || !target.matches("input")) return;
      hideFlash();
      if (fieldMap[target.name] || fieldOf(target)) clearFieldError(target);
    });

    /* Registered last so its verdict survives the clearing above. */
    initPhone();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
