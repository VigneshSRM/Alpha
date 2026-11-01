"""Password policy — the single source of truth for both server and browser.

Policy (as requested by Alpha Group of India):

* minimum 12 characters,
* at least 1 uppercase letter,
* at least 1 lowercase letter,
* at least 2 digits,
* at least 2 special characters,
* plus a complexity gate: no common/breached-style passwords, no long runs of
  the same character, no keyboard/alphabet sequences and nothing derived from
  the user's own name or e-mail address.

``POLICY`` is serialised into the page so the JavaScript validator and this
module can never drift apart.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from typing import Iterable, Optional

POLICY: dict[str, int] = {
    "minLength": 12,
    "maxLength": 128,
    "minUppercase": 1,
    "minLowercase": 1,
    "minDigits": 2,
    "minSpecial": 2,
    "maxRepeatedRun": 2,
    "sequenceLength": 4,
}

SPECIAL_CHARS = r"""!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""

_UPPER_RE = re.compile(r"[A-Z]")
_LOWER_RE = re.compile(r"[a-z]")
_DIGIT_RE = re.compile(r"\d")
_SPECIAL_RE = re.compile("[" + re.escape(SPECIAL_CHARS) + "]")

# Small, kept-in-sync list of very common passwords / stems.  Anything that
# *contains* one of these after normalisation is rejected outright.
COMMON_STEMS = (
    "password",
    "passw0rd",
    "p@ssword",
    "qwerty",
    "letmein",
    "welcome",
    "admin",
    "iloveyou",
    "monkey",
    "dragon",
    "master",
    "sunshine",
    "princess",
    "football",
    "baseball",
    "superman",
    "trustno1",
    "abc123",
    "123456",
    "111111",
    "000000",
    "changeme",
    "secret",
)

SEQUENCES = (
    "abcdefghijklmnopqrstuvwxyz",
    "0123456789",
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm",
)


@dataclass(frozen=True)
class Rule:
    """A single, user-facing password requirement."""

    key: str
    label: str
    hint: str


RULES: tuple[Rule, ...] = (
    Rule("length", "12 characters minimum", "Aim for a memorable passphrase"),
    Rule("uppercase", "1 uppercase letter", "A–Z"),
    Rule("lowercase", "1 lowercase letter", "a–z"),
    Rule("digits", "2 numbers", "0–9"),
    Rule("special", "2 special characters", "! @ # $ % & *"),
    Rule("complexity", "No common patterns", "No names, e-mail or sequences"),
)

RULE_LABELS = {rule.key: rule.label for rule in RULES}
RULE_HINTS = {rule.key: rule.hint for rule in RULES}


@dataclass
class PolicyResult:
    valid: bool
    checks: dict[str, bool]
    violations: list[str] = field(default_factory=list)
    score: int = 0
    strength: str = "weak"

    def as_dict(self) -> dict:
        return {
            "valid": self.valid,
            "checks": self.checks,
            "violations": self.violations,
            "score": self.score,
            "strength": self.strength,
        }


def _normalise(value: str) -> str:
    """Lowercase + strip accents so ``Pàssw0rd`` still trips the blocklist."""
    decomposed = unicodedata.normalize("NFKD", value)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch)).lower()


def _has_sequence(password: str) -> bool:
    lowered = password.lower()
    for seq in SEQUENCES:
        reversed_seq = seq[::-1]
        for source in (seq, reversed_seq):
            size = POLICY["sequenceLength"]
            for start in range(len(source) - size + 1):
                if source[start : start + size] in lowered:
                    return True
    return False


def _has_repeated_run(password: str) -> bool:
    limit = POLICY["maxRepeatedRun"]
    run = 1
    for index in range(1, len(password)):
        if password[index] == password[index - 1]:
            run += 1
            if run > limit:
                return True
        else:
            run = 1
    return False


def _has_common_stem(password: str) -> bool:
    normalised = _normalise(password)
    compact = re.sub(r"[^a-z0-9]", "", normalised)
    return any(stem in normalised or stem in compact for stem in COMMON_STEMS)


def _personal_tokens(context: Optional[dict]) -> list[str]:
    """Name / e-mail fragments that must not appear inside the password."""
    if not context:
        return []
    tokens: list[str] = []
    email = (context.get("email") or "").strip().lower()
    if email:
        local = email.split("@", 1)[0]
        tokens.append(local)
        tokens.extend(part for part in re.split(r"[._\-+]", local) if part)
    name = (context.get("name") or "").strip().lower()
    if name:
        tokens.extend(part for part in re.split(r"[\s._\-]+", name) if part)
    # Only meaningful fragments; 2-letter pieces cause false positives.
    return sorted({token for token in tokens if len(token) >= 3})


def _leaks_personal_data(password: str, context: Optional[dict]) -> bool:
    lowered = _normalise(password)
    return any(token in lowered for token in _personal_tokens(context))


def _score(password: str, checks: dict[str, bool]) -> int:
    points = sum(12 for ok in checks.values() if ok)  # 6 rules -> 72
    length = len(password)
    if length >= POLICY["minLength"]:
        points += 6
    if length >= POLICY["minLength"] + 4:
        points += 8
    if len(set(password)) >= 10:
        points += 7
    if _UPPER_RE.search(password) and _LOWER_RE.search(password):
        points += 4
    if len(_SPECIAL_RE.findall(password)) >= 3:
        points += 3
    return max(0, min(100, points))


def _strength(score: int) -> str:
    if score < 40:
        return "weak"
    if score < 60:
        return "fair"
    if score < 82:
        return "good"
    return "strong"


def evaluate(
    password: str,
    context: Optional[dict] = None,
    *,
    extra_checks: Optional[Iterable[str]] = None,
) -> PolicyResult:
    """Validate ``password`` against the policy.

    ``context`` may contain ``email`` and/or ``name`` so that the password can
    be checked for personal-data reuse.
    """
    password = password or ""
    checks = {
        "length": len(password) >= POLICY["minLength"],
        "uppercase": len(_UPPER_RE.findall(password)) >= POLICY["minUppercase"],
        "lowercase": len(_LOWER_RE.findall(password)) >= POLICY["minLowercase"],
        "digits": len(_DIGIT_RE.findall(password)) >= POLICY["minDigits"],
        "special": len(_SPECIAL_RE.findall(password)) >= POLICY["minSpecial"],
        "complexity": bool(password)
        and not _has_common_stem(password)
        and not _has_sequence(password)
        and not _has_repeated_run(password)
        and not _leaks_personal_data(password, context),
    }

    for key in extra_checks or ():
        checks[key] = checks.get(key, True) and True

    violations: list[str] = []
    if not password:
        violations.append("Enter a password.")
    else:
        if len(password) > POLICY["maxLength"]:
            violations.append(
                f"Password must be at most {POLICY['maxLength']} characters."
            )
            checks["length"] = False
        if not checks["length"]:
            violations.append(
                f"Password must be at least {POLICY['minLength']} characters long."
            )
        if not checks["uppercase"]:
            violations.append(
                f"Add at least {POLICY['minUppercase']} uppercase letter (A–Z)."
            )
        if not checks["lowercase"]:
            violations.append(
                f"Add at least {POLICY['minLowercase']} lowercase letter (a–z)."
            )
        if not checks["digits"]:
            violations.append(f"Add at least {POLICY['minDigits']} numbers (0–9).")
        if not checks["special"]:
            violations.append(
                f"Add at least {POLICY['minSpecial']} special characters "
                "(! @ # $ % & *)."
            )
        if not checks["complexity"]:
            if _has_common_stem(password):
                violations.append(
                    "That password contains a well-known dictionary word. "
                    "Choose something unique."
                )
            elif _leaks_personal_data(password, context):
                violations.append(
                    "Do not use your name or e-mail address in the password."
                )
            elif _has_sequence(password):
                violations.append(
                    "Avoid keyboard or alphabet sequences such as 'abcd' or '1234'."
                )
            elif _has_repeated_run(password):
                violations.append(
                    "Avoid repeating the same character more than twice in a row."
                )
            else:
                violations.append("Password is not complex enough.")

    score = _score(password, checks)
    return PolicyResult(
        valid=all(checks.values()),
        checks=checks,
        violations=violations,
        score=score,
        strength=_strength(score),
    )


def is_valid(password: str, context: Optional[dict] = None) -> bool:
    return evaluate(password, context).valid
