/*
 * The business-profile page.
 *
 * Nothing here decides whether the form is valid — the server does that and
 * paints the verdict. This only keeps the phone hint honest and the phone field
 * to digits, which is politeness, not validation.
 */
(function () {
  "use strict";

  var form = document.getElementById("onboardingForm");
  if (!form) return;

  var dial = form.querySelector("#country_code");
  var phone = form.querySelector("#phone");
  var hint = form.querySelector("[data-phone-hint]");

  function updateHint() {
    if (!dial || !hint) return;
    var option = dial.options[dial.selectedIndex];
    if (!option) return;
    var range = option.getAttribute("data-digits") || "10-10";
    var parts = range.split("-");
    var where = option.getAttribute("data-name") || option.value;
    hint.textContent =
      parts[0] === parts[1]
        ? parts[0] + " digits for " + where
        : parts[0] + " to " + parts[1] + " digits for " + where;
  }

  if (dial) {
    dial.addEventListener("change", updateHint);
    updateHint();
  }

  if (phone) {
    phone.addEventListener("input", function () {
      var clean = phone.value.replace(/[^\d\s]/g, "");
      if (clean !== phone.value) phone.value = clean;
    });
  }

  /* The food-licence block belongs to restaurants only. The server decides the
     first time (th:hidden); from then on the radios drive it. */
  var licence = form.querySelector("[data-fssai]");

  function syncLicence() {
    if (!licence) return;
    var chosen = form.querySelector('input[name="vertical"]:checked');
    licence.hidden = !(chosen && chosen.value === "Restaurant");
  }

  form.querySelectorAll('input[name="vertical"]').forEach(function (radio) {
    radio.addEventListener("change", syncLicence);
  });
  syncLicence();

  /* A field the server complained about clears as soon as it is edited —
     the real answer arrives with the next submit. */
  form.querySelectorAll(".field.is-invalid").forEach(function (field) {
    var control = field.querySelector("input, select");
    if (!control) return;
    control.addEventListener("input", function () {
      field.classList.remove("is-invalid");
      var message = field.querySelector(".field__error");
      if (message) message.textContent = "";
    }, { once: true });
  });
})();
