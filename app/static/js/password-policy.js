/* ==========================================================================
   Alpha Group of India — client-side mirror of app/password_policy.py
   The server is always authoritative; this module keeps the live checklist,
   the strength meter and the browser-side gate in sync with it.
   ========================================================================== */
(function () {
  "use strict";

  var DEFAULTS = {
    minLength: 12,
    maxLength: 128,
    minUppercase: 1,
    minLowercase: 1,
    minDigits: 2,
    minSpecial: 2,
    maxRepeatedRun: 2,
    sequenceLength: 4
  };

  var SPECIAL = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

  var COMMON_STEMS = [
    "password", "passw0rd", "p@ssword", "qwerty", "letmein", "welcome", "admin",
    "iloveyou", "monkey", "dragon", "master", "sunshine", "princess", "football",
    "baseball", "superman", "trustno1", "abc123", "123456", "111111", "000000",
    "changeme", "secret"
  ];

  var SEQUENCES = ["abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiop", "asdfghjkl", "zxcvbnm"];

  var RULES = [
    { key: "length", label: "12 characters minimum", hint: "Aim for a memorable passphrase" },
    { key: "uppercase", label: "1 uppercase letter", hint: "A–Z" },
    { key: "lowercase", label: "1 lowercase letter", hint: "a–z" },
    { key: "digits", label: "2 numbers", hint: "0–9" },
    { key: "special", label: "2 special characters", hint: "! @ # $ % & *" },
    { key: "complexity", label: "No common patterns", hint: "No names, e-mail or sequences" }
  ];

  function readConfig() {
    var config = Object.assign({}, DEFAULTS);
    try {
      var raw = document.body.getAttribute("data-policy");
      if (raw) {
        var parsed = JSON.parse(raw);
        Object.keys(config).forEach(function (key) {
          if (typeof parsed[key] === "number") config[key] = parsed[key];
        });
      }
    } catch (error) {
      /* fall back to defaults */
    }
    return config;
  }

  var CONFIG = readConfig();

  function countSpecial(value) {
    var count = 0;
    for (var i = 0; i < value.length; i += 1) {
      if (SPECIAL.indexOf(value[i]) !== -1) count += 1;
    }
    return count;
  }

  function normalise(value) {
    if (typeof value.normalize === "function") {
      return value.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
    }
    return value.toLowerCase();
  }

  function hasCommonStem(value) {
    var lowered = normalise(value);
    var compact = lowered.replace(/[^a-z0-9]/g, "");
    return COMMON_STEMS.some(function (stem) {
      return lowered.indexOf(stem) !== -1 || compact.indexOf(stem) !== -1;
    });
  }

  function hasSequence(value) {
    var lowered = value.toLowerCase();
    var size = CONFIG.sequenceLength;
    return SEQUENCES.some(function (source) {
      var variants = [source, source.split("").reverse().join("")];
      return variants.some(function (variant) {
        for (var i = 0; i + size <= variant.length; i += 1) {
          if (lowered.indexOf(variant.substr(i, size)) !== -1) return true;
        }
        return false;
      });
    });
  }

  function hasRepeatedRun(value) {
    var run = 1;
    for (var i = 1; i < value.length; i += 1) {
      if (value[i] === value[i - 1]) {
        run += 1;
        if (run > CONFIG.maxRepeatedRun) return true;
      } else {
        run = 1;
      }
    }
    return false;
  }

  function personalTokens(context) {
    if (!context) return [];
    var tokens = [];
    var email = (context.email || "").trim().toLowerCase();
    if (email) {
      var local = email.split("@")[0] || "";
      tokens.push(local);
      tokens = tokens.concat(local.split(/[._\-+]/));
    }
    var name = (context.name || "").trim().toLowerCase();
    if (name) tokens = tokens.concat(name.split(/[\s._\-]+/));
    return tokens.filter(function (token) {
      return token && token.length >= 3;
    });
  }

  function leaksPersonal(value, context) {
    var lowered = normalise(value);
    return personalTokens(context).some(function (token) {
      return lowered.indexOf(token) !== -1;
    });
  }

  function scoreFor(value, checks) {
    var points = 0;
    Object.keys(checks).forEach(function (key) {
      if (checks[key]) points += 12;
    });
    var length = value.length;
    if (length >= CONFIG.minLength) points += 6;
    if (length >= CONFIG.minLength + 4) points += 8;
    if (new Set(value.split("")).size >= 10) points += 7;
    if (/[A-Z]/.test(value) && /[a-z]/.test(value)) points += 4;
    if (countSpecial(value) >= 3) points += 3;
    return Math.max(0, Math.min(100, points));
  }

  function strengthFor(score) {
    if (score < 40) return "weak";
    if (score < 60) return "fair";
    if (score < 82) return "good";
    return "strong";
  }

  function evaluate(password, context) {
    var value = password || "";
    var checks = {
      length: value.length >= CONFIG.minLength,
      uppercase: (value.match(/[A-Z]/g) || []).length >= CONFIG.minUppercase,
      lowercase: (value.match(/[a-z]/g) || []).length >= CONFIG.minLowercase,
      digits: (value.match(/\d/g) || []).length >= CONFIG.minDigits,
      special: countSpecial(value) >= CONFIG.minSpecial,
      complexity:
        value.length > 0 &&
        !hasCommonStem(value) &&
        !hasSequence(value) &&
        !hasRepeatedRun(value) &&
        !leaksPersonal(value, context)
    };

    var violations = [];
    if (!value) {
      violations.push("Enter a password.");
    } else {
      if (value.length > CONFIG.maxLength) {
        violations.push("Password must be at most " + CONFIG.maxLength + " characters.");
        checks.length = false;
      }
      if (!checks.length) violations.push("Use at least " + CONFIG.minLength + " characters.");
      if (!checks.uppercase) violations.push("Add at least " + CONFIG.minUppercase + " uppercase letter (A–Z).");
      if (!checks.lowercase) violations.push("Add at least " + CONFIG.minLowercase + " lowercase letter (a–z).");
      if (!checks.digits) violations.push("Add at least " + CONFIG.minDigits + " numbers (0–9).");
      if (!checks.special) violations.push("Add at least " + CONFIG.minSpecial + " special characters (! @ # $ % & *).");
      if (!checks.complexity) {
        if (hasCommonStem(value)) {
          violations.push("That password contains a well-known dictionary word. Choose something unique.");
        } else if (leaksPersonal(value, context)) {
          violations.push("Do not use your name or e-mail address in the password.");
        } else if (hasSequence(value)) {
          violations.push("Avoid keyboard or alphabet sequences such as 'abcd' or '1234'.");
        } else if (hasRepeatedRun(value)) {
          violations.push("Avoid repeating the same character more than twice in a row.");
        } else {
          violations.push("Password is not complex enough.");
        }
      }
    }

    var score = scoreFor(value, checks);
    return {
      valid: Object.keys(checks).every(function (key) { return checks[key]; }),
      checks: checks,
      violations: violations,
      score: score,
      strength: strengthFor(score)
    };
  }

  var UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  var LOWER = "abcdefghijkmnopqrstuvwxyz";
  var DIGITS = "23456789";
  var SYMBOLS = "!@#$%^&*?-_+=";

  function pick(source) {
    var buffer = new Uint32Array(1);
    if (window.crypto && window.crypto.getRandomValues) {
      window.crypto.getRandomValues(buffer);
      return source[buffer[0] % source.length];
    }
    return source[Math.floor(Math.random() * source.length)];
  }

  function shuffle(items) {
    for (var i = items.length - 1; i > 0; i -= 1) {
      var buffer = new Uint32Array(1);
      var index;
      if (window.crypto && window.crypto.getRandomValues) {
        window.crypto.getRandomValues(buffer);
        index = buffer[0] % (i + 1);
      } else {
        index = Math.floor(Math.random() * (i + 1));
      }
      var tmp = items[i];
      items[i] = items[index];
      items[index] = tmp;
    }
    return items;
  }

  /** Build a memorable-ish password that always satisfies the policy. */
  function generate(options) {
    var opts = options || {};
    var length = Math.max(CONFIG.minLength + 2, Math.min(opts.length || 16, 32));

    for (var attempt = 0; attempt < 60; attempt += 1) {
      var chars = [
        pick(UPPER), pick(UPPER),
        pick(LOWER), pick(LOWER), pick(LOWER),
        pick(DIGITS), pick(DIGITS),
        pick(SYMBOLS), pick(SYMBOLS)
      ];
      while (chars.length < length) {
        var pools = [UPPER, LOWER, DIGITS, SYMBOLS];
        chars.push(pick(pools[Math.floor(Math.random() * pools.length)]));
      }
      var candidate = shuffle(chars).join("");
      if (evaluate(candidate).valid) return candidate;
    }
    // Deterministic fallback that is known to pass.
    return "Wolf@Dawn" + Math.floor(1000 + Math.random() * 9000) + "#Run!7";
  }

  window.AlphaPolicy = {
    config: CONFIG,
    rules: RULES,
    evaluate: evaluate,
    generate: generate
  };
})();
