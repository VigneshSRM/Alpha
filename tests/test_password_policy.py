"""Unit tests for the password policy (server side)."""

from __future__ import annotations

import unittest

from app.password_policy import POLICY, evaluate, is_valid


class PasswordPolicyTests(unittest.TestCase):
    def test_valid_password_is_accepted(self):
        self.assertTrue(is_valid("Wolf@Runs#Dawn27"))

    def test_short_password_is_rejected(self):
        result = evaluate("Ab1!Ab1!Ab1")
        self.assertFalse(result.checks["length"])
        self.assertFalse(result.valid)

    def test_requires_one_uppercase(self):
        result = evaluate("wolf@runs#dawn27")
        self.assertFalse(result.checks["uppercase"])
        self.assertFalse(result.valid)

    def test_requires_one_lowercase(self):
        result = evaluate("WOLF@RUNS#DAWN27")
        self.assertFalse(result.checks["lowercase"])
        self.assertFalse(result.valid)

    def test_requires_two_digits(self):
        result = evaluate("Wolf@Runs#Dawn7")
        self.assertFalse(result.checks["digits"])
        self.assertFalse(result.valid)

    def test_requires_two_special_characters(self):
        result = evaluate("WolfRunsDawn27")
        self.assertFalse(result.checks["special"])
        self.assertFalse(result.valid)

    def test_common_password_is_rejected(self):
        result = evaluate("Password@123!X")
        self.assertFalse(result.checks["complexity"])
        self.assertIn("dictionary", " ".join(result.violations).lower())

    def test_alphabet_sequence_is_rejected(self):
        result = evaluate("Abcd@Efgh12!X")
        self.assertFalse(result.checks["complexity"])

    def test_repeated_run_is_rejected(self):
        result = evaluate("Wooo@Runs#27!")
        self.assertFalse(result.checks["complexity"])

    def test_personal_data_is_rejected(self):
        result = evaluate(
            "Vignesh@Group27!",
            {"email": "vignesh@alphagroup.in", "name": "Vignesh S"},
        )
        self.assertFalse(result.checks["complexity"])
        self.assertIn("name or e-mail", " ".join(result.violations))

    def test_every_rule_is_reported(self):
        result = evaluate("abc")
        self.assertEqual(
            set(result.checks),
            {"length", "uppercase", "lowercase", "digits", "special", "complexity"},
        )
        self.assertGreaterEqual(len(result.violations), 4)

    def test_score_and_strength_increase(self):
        weak = evaluate("abc")
        strong = evaluate("Wolf@Runs#Dawn27!")
        self.assertLess(weak.score, strong.score)
        self.assertEqual(strong.strength, "strong")

    def test_max_length(self):
        long_password = "Aa1!" + ("Xy9*" * 40)
        result = evaluate(long_password)
        self.assertFalse(result.valid)
        self.assertGreater(len(long_password), POLICY["maxLength"])

    def test_policy_numbers_match_the_brief(self):
        self.assertEqual(POLICY["minLength"], 12)
        self.assertEqual(POLICY["minUppercase"], 1)
        self.assertEqual(POLICY["minLowercase"], 1)
        self.assertEqual(POLICY["minDigits"], 2)
        self.assertEqual(POLICY["minSpecial"], 2)


if __name__ == "__main__":
    unittest.main()
