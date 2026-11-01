/* ==========================================================================
   Alpha Group of India — strong password generator
   Generation only. Nothing here decides whether a password is acceptable:
   the server (Supabase Auth + app/password_policy.py) owns that verdict.
   ========================================================================== */
(function () {
  "use strict";

  var DEFAULTS = { minLength: 12, maxLength: 128, minDigits: 2, minSpecial: 2 };
  var CONFIG = Object.assign({}, DEFAULTS);

  try {
    var raw = document.body.getAttribute("data-policy");
    if (raw) {
      var parsed = JSON.parse(raw);
      Object.keys(CONFIG).forEach(function (key) {
        if (typeof parsed[key] === "number") CONFIG[key] = parsed[key];
      });
    }
  } catch (error) {
    /* keep defaults */
  }

  var UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  var LOWER = "abcdefghijkmnopqrstuvwxyz";
  var DIGITS = "23456789";
  var SYMBOLS = "!@#$%^&*?-_+=";

  function randomInt(max) {
    if (window.crypto && window.crypto.getRandomValues) {
      var buffer = new Uint32Array(1);
      window.crypto.getRandomValues(buffer);
      return buffer[0] % max;
    }
    return Math.floor(Math.random() * max);
  }

  function pick(source) {
    return source.charAt(randomInt(source.length));
  }

  function shuffle(items) {
    for (var i = items.length - 1; i > 0; i -= 1) {
      var j = randomInt(i + 1);
      var tmp = items[i];
      items[i] = items[j];
      items[j] = tmp;
    }
    return items;
  }

  /**
   * Build a password that structurally satisfies the published policy.
   * It still goes through server-side validation when the form is submitted.
   */
  function generate(length) {
    var size = Math.max(CONFIG.minLength, Math.min(length || 16, 32));
    var chars = [
      pick(UPPER),
      pick(UPPER),
      pick(LOWER),
      pick(LOWER),
      pick(LOWER),
      pick(DIGITS),
      pick(DIGITS),
      pick(SYMBOLS),
      pick(SYMBOLS)
    ];
    var pools = [UPPER, LOWER, DIGITS, SYMBOLS];
    while (chars.length < size) {
      chars.push(pick(pools[randomInt(pools.length)]));
    }
    return shuffle(chars).join("");
  }

  window.AlphaPassword = { generate: generate, config: CONFIG };
})();
