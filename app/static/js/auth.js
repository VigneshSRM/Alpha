/* ==========================================================================
   Alpha Group of India — login / signup interactions
   Live policy checklist, password visibility, policy dialog, async submit.
   ========================================================================== */
(function () {
  "use strict";

  var policy = window.AlphaPolicy;
  if (!policy) return;

  function $(id) { return document.getElementById(id); }

  function fieldOf(input) {
    return input ? input.closest("[data-field]") : null;
  }

  function setFieldError(input, message) {
    var field = fieldOf(input);
    if (!field) return;
    var error = field.querySelector(".field__error");
    if (error) error.textContent = message || "";
    field.classList.toggle("is-invalid", Boolean(message));
    input.setAttribute("aria-invalid", message ? "true" : "false");
  }

  function setFieldValid(input, valid) {
    var field = fieldOf(input);
    if (!field) return;
    field.classList.toggle("is-valid", Boolean(valid));
  }

  function showAlert(message, kind) {
    var alert = document.querySelector("[data-server-alert]");
    if (!alert) {
      alert = document.createElement("div");
      alert.className = "alert alert--" + (kind || "error");
      alert.setAttribute("role", "alert");
      alert.setAttribute("data-server-alert", "");
      var head = document.querySelector(".panel__head");
      if (head && head.parentNode) head.parentNode.insertBefore(alert, head.nextSibling);
    }
    alert.className = "alert alert--" + (kind || "error");
    alert.innerHTML = "";
    var span = document.createElement("span");
    span.textContent = message;
    alert.appendChild(span);
    alert.hidden = false;
    alert.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }

  function hideAlert() {
    var alert = document.querySelector("[data-server-alert]");
    if (alert) {
      alert.hidden = true;
      alert.textContent = "";
    }
  }

  function showSuccess(message) {
    var box = $("formSuccess");
    var text = $("formSuccessText");
    if (!box || !text) return;
    text.textContent = message;
    box.hidden = false;
  }

  /**
   * Reflect the revealed/hidden state on a visibility button.
   * SVG elements are not HTMLElements, so `svg.hidden` is a no-op expando —
   * the class + attribute pair below is what actually paints the right glyph.
   */
  function setRevealState(button, reveal) {
    if (!button) return;
    button.classList.toggle("is-revealed", Boolean(reveal));
    button.setAttribute("aria-pressed", reveal ? "true" : "false");
    button.setAttribute("aria-label", reveal ? "Hide password" : "Show password");
    button.title = reveal ? "Hide password" : "Show password";
    var eye = button.querySelector(".icon-eye");
    var eyeOff = button.querySelector(".icon-eye-off");
    if (eye) {
      if (reveal) eye.setAttribute("hidden", "hidden");
      else eye.removeAttribute("hidden");
    }
    if (eyeOff) {
      if (reveal) eyeOff.removeAttribute("hidden");
      else eyeOff.setAttribute("hidden", "hidden");
    }
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
      try { input.setSelectionRange(end, end); } catch (error) { /* type=email */ }
    });
  }

  function bindCapsLock(input, hint) {
    if (!input || !hint) return;
    function update(event) {
      var on = false;
      if (event && typeof event.getModifierState === "function") {
        on = event.getModifierState("CapsLock");
      }
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

    return { open: open, close: close, element: modal, isOpen: function () { return !modal.hidden; } };
  }

  /* ------------------------------------------------------------------ */
  /* Boot                                                               */
  /* ------------------------------------------------------------------ */
  function init() {
    var form = $("loginForm") || $("signupForm");
    if (!form) return;

    var mode = form.dataset.mode || "login";
    var emailInput = $("email");
    var nameInput = $("name");
    var passwordInput = $("password");
    var confirmInput = $("confirm_password");
    var passwordError = $("passwordError");
    var confirmError = $("confirmError");
    var rulesList = $("passwordRules");
    var meter = $("strengthMeter");
    var meterLabel = $("strengthLabel");
    var capsHint = $("capsLock");
    var submitBtn = $("submitBtn");
    var emailTimer = null;

    /* ---- rules + meter ---- */
    function renderRules(result) {
      if (!rulesList) return;
      Array.prototype.forEach.call(rulesList.querySelectorAll("[data-rule]"), function (item) {
        var key = item.getAttribute("data-rule");
        var met = Boolean(result.checks[key]);
        item.classList.toggle("is-met", met);
        item.classList.toggle("is-unmet", !met);
        item.setAttribute("aria-checked", met ? "true" : "false");
      });
    }

    function renderMeter(value, result) {
      if (!meter || !meterLabel) return;
      if (!value) {
        meter.hidden = true;
        return;
      }
      meter.hidden = false;
      meter.setAttribute("data-strength", result.strength);
      var labels = { weak: "Weak", fair: "Fair", good: "Good", strong: "Strong" };
      meterLabel.textContent = labels[result.strength] || "Weak";
    }

    function context() {
      return {
        email: emailInput ? emailInput.value : "",
        name: nameInput ? nameInput.value : ""
      };
    }

    function validatePassword(options) {
      if (!passwordInput) return { valid: true, result: null };
      var opts = options || {};
      var value = passwordInput.value;
      var result = policy.evaluate(value, context());
      renderRules(result);
      renderMeter(value, result);

      if (opts.showErrors) {
        if (!value) {
          setFieldError(passwordInput, "Enter your password.");
        } else if (!result.valid) {
          setFieldError(passwordInput, result.violations[0] || "Password does not meet the policy.");
        } else {
          setFieldError(passwordInput, "");
        }
      } else if (result.valid) {
        setFieldError(passwordInput, "");
      }
      if (passwordError && !opts.showErrors && !value) passwordError.textContent = "";
      setFieldValid(passwordInput, Boolean(value) && result.valid);
      return { valid: result.valid, result: result };
    }

    if (passwordInput) {
      passwordInput.addEventListener("input", function () {
        validatePassword({ showErrors: false });
        if (confirmInput && confirmInput.value) validateConfirm(false);
      });
      passwordInput.addEventListener("blur", function () {
        if (passwordInput.value) validatePassword({ showErrors: true });
      });
      validatePassword({ showErrors: false });
      var savedError = passwordError ? passwordError.getAttribute("data-error") : "";
      if (savedError) setFieldError(passwordInput, savedError);
    }

    /* ---- confirm password ---- */
    function validateConfirm(showErrors) {
      if (!confirmInput) return true;
      var match = confirmInput.value.length > 0 && confirmInput.value === passwordInput.value;
      var hint = $("confirmHint");
      if (hint) hint.hidden = !match;
      setFieldValid(confirmInput, match);
      if (showErrors) {
        if (!confirmInput.value) setFieldError(confirmInput, "Re-enter your password.");
        else if (!match) setFieldError(confirmInput, "Passwords do not match.");
        else setFieldError(confirmInput, "");
      } else if (match) {
        setFieldError(confirmInput, "");
      }
      return match;
    }

    if (confirmInput) {
      confirmInput.addEventListener("input", function () { validateConfirm(false); });
      confirmInput.addEventListener("blur", function () {
        if (confirmInput.value || passwordInput.value) validateConfirm(true);
      });
      var confirmSaved = confirmError ? confirmError.textContent.trim() : "";
      if (confirmSaved) setFieldError(confirmInput, confirmSaved);
    }

    /* ---- e-mail ---- */
    var EMAIL_RE = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/;

    function validateEmail(showErrors) {
      if (!emailInput) return true;
      var value = emailInput.value.trim();
      var message = "";
      if (!value) message = "Enter your e-mail address.";
      else if (!EMAIL_RE.test(value)) message = "Enter a valid e-mail address, e.g. name@company.com.";
      if (showErrors) setFieldError(emailInput, message);
      else if (!message) setFieldError(emailInput, "");
      setFieldValid(emailInput, Boolean(value) && !message);
      return !message;
    }

    if (emailInput) {
      emailInput.addEventListener("input", function () {
        if (emailTimer) window.clearTimeout(emailTimer);
        var clear = $("clearEmail");
        if (clear) clear.hidden = emailInput.value.length === 0;
        emailTimer = window.setTimeout(function () { validateEmail(false); }, 320);
        if (fieldOf(emailInput) && fieldOf(emailInput).classList.contains("is-invalid")) validateEmail(false);
      });
      emailInput.addEventListener("blur", function () { validateEmail(true); });
      var emailError = $("emailError");
      var serverEmailError = emailError ? emailError.textContent.trim() : "";
      if (serverEmailError) setFieldError(emailInput, serverEmailError);

      var clearBtn = $("clearEmail");
      if (clearBtn) {
        clearBtn.hidden = emailInput.value.length === 0;
        clearBtn.addEventListener("click", function () {
          emailInput.value = "";
          clearBtn.hidden = true;
          emailInput.focus();
          setFieldError(emailInput, "");
        });
      }
    }

    if (nameInput) {
      nameInput.addEventListener("input", function () {
        if (nameInput.value.trim().length >= 2) setFieldError(nameInput, "");
        if (passwordInput && passwordInput.value) validatePassword({ showErrors: false });
      });
    }

    /* ---- password visibility + caps lock ---- */
    bindVisibility($("togglePassword"), passwordInput);
    bindVisibility($("toggleConfirm"), confirmInput);
    bindCapsLock(passwordInput, capsHint);

    /* ---- policy dialog ---- */
    var policyModal = createModal($("policyModal"));
    var policyBtn = $("policyInfoBtn");
    if (policyModal && policyBtn) {
      policyBtn.addEventListener("click", function () { policyModal.open(); });
      Array.prototype.forEach.call(
        document.querySelectorAll("[data-close-policy]"),
        function (node) { node.addEventListener("click", function () { policyModal.close(); }); }
      );
    }

    function fillStrongPassword() {
      var generated = policy.generate({ length: 16 });
      if (passwordInput) {
        passwordInput.value = generated;
        passwordInput.type = "text";
        setRevealState($("togglePassword"), true);
        if (confirmInput) confirmInput.value = generated;
        validatePassword({ showErrors: false });
        validateConfirm(false);
        passwordInput.focus({ preventScroll: true });
        var end = generated.length;
        try { passwordInput.setSelectionRange(end, end); } catch (error) { /* noop */ }
      }
    }

    Array.prototype.forEach.call(
      document.querySelectorAll("[data-suggest-password]"),
      function (node) {
        node.addEventListener("click", function () {
          if (policyModal) policyModal.close();
          fillStrongPassword();
        });
      }
    );

    /* ---- demo credentials ---- */
    var demoBtn = $("fillDemo");
    if (demoBtn) {
      demoBtn.addEventListener("click", function () {
        demoBtn.dataset.email = demoBtn.dataset.email || "";
        var card = demoBtn.closest(".demo-card");
        var codes = card ? card.querySelectorAll("code") : [];
        if (emailInput && codes[0]) emailInput.value = codes[0].textContent.trim();
        if (passwordInput && codes[1]) {
          passwordInput.value = codes[1].textContent.trim();
          passwordInput.dispatchEvent(new Event("input", { bubbles: true }));
        }
        validateEmail(false);
        validatePassword({ showErrors: false });
        hideAlert();
        if (passwordInput) passwordInput.focus();
      });
    }

    /* ---- forgot password ---- */
    var forgotModal = $("forgotModal");
    var forgotCtl = createModal(forgotModal);
    var forgotBtn = $("forgotBtn");

    if (forgotBtn) {
      forgotBtn.addEventListener("click", function () {
        var enabled = forgotModal && forgotModal.dataset.resetEnabled === "true";
        if (!enabled) {
          showAlert(
            "Password reset is not configured in this environment. Add Supabase keys to enable e-mail reset, or contact IT support.",
            "notice"
          );
          return;
        }
        var target = $("forgotEmail");
        if (target) {
          if (emailInput && EMAIL_RE.test(emailInput.value.trim())) target.value = emailInput.value.trim();
          var error = $("forgotEmailError");
          if (error) error.textContent = "";
          var status = $("forgotStatus");
          if (status) status.hidden = true;
        }
        if (forgotCtl) forgotCtl.open();
      });
    }

    Array.prototype.forEach.call(
      document.querySelectorAll("[data-close-forgot]"),
      function (node) { node.addEventListener("click", function () { if (forgotCtl) forgotCtl.close(); }); }
    );

    var forgotForm = $("forgotForm");
    if (forgotForm) {
      forgotForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var email = $("forgotEmail");
        var error = $("forgotEmailError");
        var status = $("forgotStatus");
        var button = $("forgotSubmit");
        var value = email ? email.value.trim() : "";

        if (!value || !EMAIL_RE.test(value)) {
          if (error) error.textContent = "Enter a valid e-mail address.";
          if (email) email.focus();
          return;
        }
        if (error) error.textContent = "";
        if (button) { button.disabled = true; button.classList.add("is-loading"); }

        fetch(forgotForm.getAttribute("action"), {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "X-Requested-With": "fetch",
            "X-CSRF-Token": forgotForm.querySelector("input[name=csrf_token]").value
          },
          body: new FormData(forgotForm),
          credentials: "same-origin"
        })
          .then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (data) {
              return { status: response.status, data: data };
            });
          })
          .then(function (result) {
            if (button) { button.disabled = false; button.classList.remove("is-loading"); }
            if (result.data && result.data.ok) {
              if (status) {
                status.textContent = result.data.message || "If an account exists, a reset link is on its way.";
                status.hidden = false;
              }
              if (email) email.value = "";
            } else if (error) {
              error.textContent = (result.data && result.data.message) || "Could not send the reset link. Try again.";
            }
          })
          .catch(function () {
            if (button) { button.disabled = false; button.classList.remove("is-loading"); }
            if (error) error.textContent = "Network error. Please check your connection and try again.";
          });
      });
    }

    /* ---- submit ---- */
    var enhanced = true;
    form.dataset.enhanced = "true";

    function setLoading(state) {
      if (!submitBtn) return;
      submitBtn.disabled = state;
      submitBtn.classList.toggle("is-loading", state);
      var label = submitBtn.querySelector(".btn__label");
      if (label) {
        if (state) {
          label.dataset.idle = label.textContent;
          label.textContent = mode === "signup" ? "Creating account…" : "Signing in…";
        } else if (label.dataset.idle) {
          label.textContent = label.dataset.idle;
        }
      }
    }

    function firstInvalidField() {
      var invalid = form.querySelector(".field.is-invalid .control__input");
      return invalid || null;
    }

    form.addEventListener("submit", function (event) {
      if (!enhanced) return;
      event.preventDefault();
      hideAlert();

      var ok = true;
      if (nameInput && mode === "signup") {
        var nameValue = nameInput.value.trim();
        if (nameValue.length < 2) {
          setFieldError(nameInput, "Enter your full name (at least 2 characters).");
          ok = false;
        } else if (nameValue.length > 80) {
          setFieldError(nameInput, "That name is too long (80 characters maximum).");
          ok = false;
        } else {
          setFieldError(nameInput, "");
        }
      }
      if (!validateEmail(true)) ok = false;
      var passwordState = validatePassword({ showErrors: true });
      if (!passwordState.valid) ok = false;
      if (confirmInput && !validateConfirm(true)) ok = false;

      if (!ok) {
        var first = firstInvalidField();
        if (first) first.focus({ preventScroll: false });
        return;
      }

      if (passwordError) passwordError.textContent = "";
      setLoading(true);

      var headers = { "Accept": "application/json", "X-Requested-With": "fetch" };
      var tokenField = form.querySelector("input[name=csrf_token]");
      if (tokenField) headers["X-CSRF-Token"] = tokenField.value;

      fetch(form.getAttribute("action"), {
        method: "POST",
        headers: headers,
        body: new FormData(form),
        credentials: "same-origin"
      })
        .then(function (response) {
          return response
            .json()
            .catch(function () { return { message: "Unexpected server response." }; })
            .then(function (data) { return { status: response.status, data: data }; });
        })
        .then(function (outcome) {
          var data = outcome.data || {};
          if (data.ok) {
            setLoading(false);
            showSuccess(data.message || "Success. Redirecting…");
            if (passwordInput) passwordInput.blur();
            var target = data.redirect || null;
            window.setTimeout(function () {
              window.location.assign(target || "/dashboard");
            }, 650);
            return;
          }

          setLoading(false);
          if (data.code === "csrf" && data.csrfToken) {
            // The session cookie was rotated/expired: re-sync the token so the
            // user can simply press the button again instead of losing input.
            var stale = form.querySelector("input[name=csrf_token]");
            if (stale) stale.value = data.csrfToken;
          }
          var message = data.message || "Sign-in failed. Please try again.";
          if (data.attemptsRemaining === 0) {
            message = "Too many failed attempts. This account is temporarily locked — try again shortly.";
          }
          showAlert(message, "error");

          if (data.errors && typeof data.errors === "object") {
            Object.keys(data.errors).forEach(function (key) {
              var map = {
                email: emailInput,
                name: nameInput,
                password: passwordInput,
                confirm_password: confirmInput
              };
              var input = map[key];
              if (input) setFieldError(input, data.errors[key]);
            });
          }
          if (data.field) {
            var mapAll = {
              email: emailInput,
              name: nameInput,
              password: passwordInput,
              confirm_password: confirmInput
            };
            var targetInput = mapAll[data.field];
            if (targetInput) targetInput.focus({ preventScroll: false });
          }
        })
        .catch(function () {
          setLoading(false);
          showAlert("Network error. Please check your connection and try again.", "error");
        });
    });

    /* Clear the alert as soon as the user starts correcting things. */
    form.addEventListener("input", function (event) {
      if (event.target && event.target.matches("input")) hideAlert();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
