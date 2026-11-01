/* ==========================================================================
   Alpha Group of India — portal interactions

   IMPORTANT: this file performs NO validation. It only improves the feel of
   the form (password visibility, caps-lock hint, dialogs, spinner) and then
   renders whatever the backend decided. Every rule — e-mail shape, password
   policy, credentials, duplicates — is judged server side.
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
    var submitBtn = $("submitBtn");
    var tokenField = form.querySelector("input[name=csrf_token]");
    var fieldMap = {
      email: emailInput,
      name: nameInput,
      password: passwordInput,
      confirm_password: confirmInput
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

    /* --- policy dialog --------------------------------------------- */
    var policyModal = createModal($("policyModal"));
    var policyBtn = $("policyInfoBtn");
    if (policyModal && policyBtn) {
      policyBtn.addEventListener("click", function () { policyModal.open(); });
      Array.prototype.forEach.call(
        document.querySelectorAll("[data-close-policy]"),
        function (node) {
          node.addEventListener("click", function () { policyModal.close(); });
        }
      );
    }

    function suggestPassword() {
      if (!window.AlphaPassword || !passwordInput) return null;
      var generated = window.AlphaPassword.generate(16);
      passwordInput.value = generated;
      // Reveal it so the person can note it down before submitting.
      passwordInput.type = "text";
      setRevealState($("togglePassword"), true);
      if (confirmInput) confirmInput.value = generated;
      clearFieldError(passwordInput);
      if (confirmInput) clearFieldError(confirmInput);
      passwordInput.focus({ preventScroll: true });
      try { passwordInput.setSelectionRange(generated.length, generated.length); } catch (error) { /* noop */ }
      return generated;
    }

    Array.prototype.forEach.call(
      document.querySelectorAll("[data-suggest-password]"),
      function (node) {
        node.addEventListener("click", function () {
          if (policyModal) policyModal.close();
          suggestPassword();
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
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
