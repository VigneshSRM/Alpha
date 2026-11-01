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

  function setFlashLink(box, options) {
    var link = box.querySelector("[data-flash-link]");
    if (!link) return;
    var action = options && options.link ? options.link : options;
    if (action && action.href) {
      link.href = action.href;
      link.textContent = action.label || "Continue";
      link.hidden = false;
    } else {
      link.hidden = true;
      link.removeAttribute("href");
    }
  }

  function showFlash(message, kind, options) {
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
    setFlashLink(box, options);
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
      setFlashLink(box, null);
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
  var REQUEST_TIMEOUT_MS = 20000;

  function postJson(url, formData, csrfToken) {
    var controller = typeof AbortController === "function" ? new AbortController() : null;
    var timer = controller
      ? window.setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MS)
      : 0;

    function stop() {
      if (timer) window.clearTimeout(timer);
    }

    return fetch(url, {
      method: "POST",
      headers: {
        "Accept": "application/json",
        "X-Requested-With": "fetch",
        "X-CSRF-Token": csrfToken || ""
      },
      body: formData,
      credentials: "same-origin",
      signal: controller ? controller.signal : undefined
    })
      .then(function (response) {
        // A proxy or an error page can answer with HTML; keep the status.
        return response
          .json()
          .catch(function () { return { message: "" }; })
          .then(function (data) {
            stop();
            return { status: response.status, data: data };
          });
      }, function (error) {
        stop();
        throw error;
      });
  }

  /* Why a submission failed, in words a person can act on. */
  function failureMessage(mode, result) {
    var status = result.status;
    var data = result.data || {};
    var code = data.code || "";
    var said = (data.message || "").trim();
    var noun = mode === "signup" ? "Registration" : "Sign-in";

    if (code === "user_exists" || status === 409) {
      return {
        text: said || "That e-mail address is already registered.",
        link: { href: "/login", label: "Sign in instead" }
      };
    }
    if (code === "signup_closed") {
      return {
        text: said || "Self-service registration is closed.",
        link: { href: "/login", label: "Back to sign in" }
      };
    }
    if (code === "rate_limited" || status === 429) {
      return { text: said || "Too many attempts. Please wait a moment and try again." };
    }
    if (code === "csrf") {
      return { text: said || "Your session expired. Please reload the page and try again." };
    }
    if (code === "validation" || status === 422) {
      return { text: said || "Some details need fixing — see the notes under the fields." };
    }
    if (code === "invalid_credentials" || status === 401) {
      return { text: said || "That e-mail and password do not match." };
    }
    if (code === "account_disabled" || status === 403) {
      return { text: said || "This account is not available. Please contact the IT desk." };
    }
    if (status >= 500) {
      return { text: noun + " failed on our side — the server could not finish it. Please try again in a moment." };
    }
    if (status === 404) {
      return { text: "The server has no " + noun.toLowerCase() + " endpoint. Please reload the page." };
    }
    if (!status) {
      return { text: "No answer from the server. Check your connection and try again." };
    }
    return { text: said || noun + " failed (the server answered " + status + ")." };
  }

  function networkMessage(error) {
    if (error && error.name === "AbortError") {
      return "The server took too long to answer. Please try again.";
    }
    if (typeof navigator !== "undefined" && navigator.onLine === false) {
      return "You appear to be offline. Reconnect and try again.";
    }
    return "Can't reach the server. Check your connection and try again.";
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


  /* ------------------------------------------------------------------ */
  /* Country picker                                                      */
  /*                                                                     */
  /* The <select> stays in the form — it is what posts and what works    */
  /* without JavaScript — but a full-width native select eats half the   */
  /* field. So it is swapped for a short "+91" button that opens a       */
  /* searchable list. The select keeps the value.                        */
  /* ------------------------------------------------------------------ */
  function initCountryPicker(select) {
    var host = select.closest("[data-picker]");
    if (!host) return null;

    var options = Array.prototype.map.call(select.options, function (option) {
      var parts = option.textContent.trim().split(" ");
      return {
        iso: option.value,
        dial: parts[0],
        name: parts.slice(1).join(" "),
        digits: option.getAttribute("data-digits") || ""
      };
    });

    var button = document.createElement("button");
    button.type = "button";
    button.className = "picker__button";
    button.setAttribute("role", "combobox");
    button.setAttribute("aria-haspopup", "listbox");
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", "countryList");
    button.setAttribute("aria-label", "Country dialling code");

    var caption = document.createElement("span");
    button.appendChild(caption);
    button.insertAdjacentHTML("beforeend",
      '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor"'
      + ' stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
      + '<path d="m6 9 6 6 6-6"/></svg>');

    var panel = document.createElement("div");
    panel.className = "picker__panel";
    panel.hidden = true;

    var search = document.createElement("input");
    search.type = "search";
    search.className = "picker__search";
    search.placeholder = "Search country or code";
    search.setAttribute("aria-label", "Search country or dialling code");
    search.autocomplete = "off";
    search.spellcheck = false;

    var list = document.createElement("ul");
    list.className = "picker__list";
    list.id = "countryList";
    list.setAttribute("role", "listbox");
    list.setAttribute("aria-label", "Countries");

    var items = options.map(function (entry) {
      var li = document.createElement("li");
      li.className = "picker__option";
      li.id = "country-" + entry.iso;
      li.setAttribute("role", "option");
      li.dataset.iso = entry.iso;
      li.dataset.search = (entry.name + " " + entry.dial + " " + entry.iso).toLowerCase();
      li.dataset.name = entry.name.toLowerCase();
      var dial = document.createElement("span");
      dial.className = "picker__dial";
      dial.textContent = entry.dial;
      var name = document.createElement("span");
      name.textContent = entry.name;
      li.appendChild(dial);
      li.appendChild(document.createTextNode(" "));
      li.appendChild(name);
      return li;
    });
    items.forEach(function (li) { list.appendChild(li); });

    var empty = document.createElement("p");
    empty.className = "picker__empty";
    empty.textContent = "No country matches that.";
    empty.hidden = true;

    panel.appendChild(search);
    panel.appendChild(empty);
    panel.appendChild(list);
    host.appendChild(button);
    host.appendChild(panel);
    host.classList.add("is-enhanced");
    // The button now carries the semantics; keep the select out of the tab order.
    select.tabIndex = -1;
    select.setAttribute("aria-hidden", "true");

    var active = null;

    function currentIndex() {
      for (var i = 0; i < items.length; i++) {
        if (items[i].dataset.iso === select.value) return i;
      }
      return 0;
    }

    function syncCaption() {
      var option = select.options[select.selectedIndex];
      caption.textContent = option ? option.textContent.trim().split(" ")[0] : "+";
    }

    function markActive(index, scroll) {
      if (active && active.classList) active.classList.remove("is-active");
      active = items[index] || null;
      if (!active) {
        button.removeAttribute("aria-activedescendant");
        return;
      }
      active.classList.add("is-active");
      button.setAttribute("aria-activedescendant", active.id);
      if (scroll) active.scrollIntoView({ block: "nearest" });
    }

    function paintSelection() {
      items.forEach(function (li) {
        li.setAttribute("aria-selected", li.dataset.iso === select.value ? "true" : "false");
      });
    }

    function open() {
      if (!panel.hidden) return;
      panel.hidden = false;
      button.setAttribute("aria-expanded", "true");
      search.value = "";
      filter("");
      var index = currentIndex();
      markActive(index, true);
      search.focus({ preventScroll: true });
      if (active) active.scrollIntoView({ block: "nearest" });
    }

    function close(refocus) {
      if (panel.hidden) return;
      panel.hidden = true;
      button.setAttribute("aria-expanded", "false");
      button.removeAttribute("aria-activedescendant");
      if (active) active.classList.remove("is-active");
      active = null;
      if (refocus) button.focus({ preventScroll: true });
    }

    function choose(iso) {
      if (!iso) return;
      select.value = iso;
      syncCaption();
      paintSelection();
      select.dispatchEvent(new Event("change", { bubbles: true }));
      close(true);
    }

    /* Ranking matters: "india" must put India above British Indian Ocean
       Territory, so prefix matches are pulled to the top of the list. */
    function filter(term) {
      var needle = term.trim().toLowerCase();
      var matched = [];
      items.forEach(function (li) {
        var name = li.dataset.name;
        var hit = !needle || li.dataset.search.indexOf(needle) >= 0;
        li.hidden = !hit;
        if (!hit) return;
        var rank = 2;
        if (!needle) rank = 2;
        else if (name.indexOf(needle) === 0) rank = 0;
        else if (name.indexOf(needle) > 0) rank = 1;
        matched.push({ li: li, rank: rank });
      });
      matched.sort(function (a, b) { return a.rank - b.rank; });
      matched.forEach(function (entry) { list.appendChild(entry.li); });
      empty.hidden = matched.length > 0;
      markActive(matched.length ? items.indexOf(matched[0].li) : -1, false);
    }

    /* DOM order, so arrow keys follow what the user sees after a search. */
    function visibleItems() {
      return Array.prototype.filter.call(list.children, function (element) {
        return element.classList.contains("picker__option") && !element.hidden;
      });
    }

    function move(step) {
      var shown = visibleItems();
      if (!shown.length) return;
      var at = shown.indexOf(active);
      var next = at < 0 ? 0 : (at + step + shown.length) % shown.length;
      markActive(items.indexOf(shown[next]), true);
    }

    button.addEventListener("click", function () {
      if (panel.hidden) open();
      else close(true);
    });

    button.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        if (panel.hidden) open();
        else if (event.key === "Enter") choose(active && active.dataset.iso);
        return;
      }
      if (event.key === "Escape") close(true);
    });

    search.addEventListener("input", function () { filter(search.value); });

    search.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown") { event.preventDefault(); move(1); }
      else if (event.key === "ArrowUp") { event.preventDefault(); move(-1); }
      else if (event.key === "Enter") { event.preventDefault(); choose(active && active.dataset.iso); }
      else if (event.key === "Escape") { event.preventDefault(); close(true); }
      else if (event.key === "Tab") close(false);
    });

    list.addEventListener("click", function (event) {
      var li = event.target.closest(".picker__option");
      if (li) choose(li.dataset.iso);
    });

    list.addEventListener("mousemove", function (event) {
      var li = event.target.closest(".picker__option");
      if (li && li !== active) markActive(items.indexOf(li), false);
    });

    document.addEventListener("click", function (event) {
      if (!panel.hidden && !host.contains(event.target)) close(false);
    });

    syncCaption();
    paintSelection();

    return { open: open, sync: syncCaption };
  }

  function initPhone() {
    var input = $("phone");
    var select = $("country_code");
    var hint = $("phoneHint");
    var form = $("authForm");
    if (!input || !select || !form) return;

    initCountryPicker(select);

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
            showFlash(
              data.message
                || (mode === "signup"
                  ? "Account created. Taking you to your dashboard…"
                  : "Signed in. Taking you to your dashboard…"),
              "success"
            );
            if (passwordInput) passwordInput.blur();
            if (submitBtn) submitBtn.disabled = true;
            /* Long enough to read the confirmation before the page moves. */
            window.setTimeout(function () {
              window.location.assign(data.redirect || "/dashboard");
            }, 2000);
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

          var failure = failureMessage(mode, result);
          var summary = failure.text;
          var first = Object.keys(errors)[0];
          // Field notes already say what is wrong; the banner says why it failed.
          if (data.code === "validation" && first) {
            summary = "That did not go through — " + errors[first];
          }
          showFlash(summary, "error", failure);
        })
        .catch(function (error) {
          setLoading(submitBtn, false);
          showFlash(networkMessage(error), "error");
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
