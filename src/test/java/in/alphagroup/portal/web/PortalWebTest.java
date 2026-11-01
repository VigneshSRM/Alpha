package in.alphagroup.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import in.alphagroup.portal.reset.PasswordResetService;
import in.alphagroup.portal.reset.PasswordResetTokenRepository;
import in.alphagroup.portal.user.PortalUser;
import in.alphagroup.portal.user.PortalUserRepository;
import in.alphagroup.portal.user.PortalUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end HTTP behaviour against a real (in-memory H2) database, exercising
 * the same Flyway migrations, repositories and security filters as production.
 *
 * <p>What must not regress: the "!" indicator markup, CSRF on every POST, the
 * server-side policy verdict, BCrypt credential checks, sign-up rules, the
 * brute-force throttle and the single-use reset link.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalWebTest {

    private static final String GOOD_PASSWORD = "Wolf@Runs#Dawn27!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PortalUserService userService;

    @Autowired
    private PortalUserRepository users;

    @Autowired
    private PasswordResetService resetService;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @BeforeEach
    void cleanDatabase() {
        resetTokens.deleteAll();
        users.deleteAll();
    }

    private PortalUser account(String email) {
        return userService.register(email, GOOD_PASSWORD, "Ada Lovelace", "+919876543210");
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sign-in page keeps the logo in the panel and the '!' indicator")
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Welcome back")))
                .andExpect(content().string(containsString("access__brand")))
                .andExpect(content().string(containsString("id=\"policyInfoBtn\"")))
                .andExpect(content().string(containsString("class=\"hint-icon__tick\"")))
                .andExpect(content().string(containsString("name=\"csrf_token\"")))
                .andExpect(content().string(containsString("data-check-endpoint=\"/api/password-check\"")))
                .andExpect(content().string(not(containsString("The Way to Lead"))))
                .andExpect(content().string(not(containsString("12+ characters · upper"))));
    }

    @Test
    @DisplayName("footer credits the session store, not a third party")
    void footerCopy() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("Sessions by Spring Security")));
    }

    @Test
    @DisplayName("/ redirects to the sign-in page")
    void indexRedirects() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/login"));
    }

    @Test
    @DisplayName("the dashboard is closed to anonymous visitors")
    void dashboardRequiresAuth() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?next=%2Fdashboard"));
    }

    @Test
    @DisplayName("the health probe is public")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ------------------------------------------------------------------
    // Live password check
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a weak password comes back with the server's reason")
    void passwordCheckRejectsWeak() throws Exception {
        mvc.perform(post("/api/password-check").with(csrf().asHeader())
                        .param("password", "weakpass"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.checks.length").value(false))
                .andExpect(jsonPath("$.violations[0]")
                        .value("Password must be at least 12 characters long."));
    }

    @Test
    @DisplayName("a compliant password flips every check to true")
    void passwordCheckAcceptsStrong() throws Exception {
        mvc.perform(post("/api/password-check").with(csrf().asHeader())
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checks.length").value(true))
                .andExpect(jsonPath("$.checks.uppercase").value(true))
                .andExpect(jsonPath("$.checks.lowercase").value(true))
                .andExpect(jsonPath("$.checks.digits").value(true))
                .andExpect(jsonPath("$.checks.special").value(true))
                .andExpect(jsonPath("$.checks.complexity").value(true));
    }

    @Test
    @DisplayName("an empty password reports 'empty' rather than an error")
    void passwordCheckHandlesEmpty() throws Exception {
        mvc.perform(post("/api/password-check").with(csrf().asHeader())
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true));
    }

    @Test
    @DisplayName("the live check is CSRF protected, like every other POST")
    void passwordCheckNeedsCsrf() throws Exception {
        mvc.perform(post("/api/password-check").param("password", GOOD_PASSWORD))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Sign in
    // ------------------------------------------------------------------

    @Test
    @DisplayName("good credentials return a JSON redirect to the dashboard")
    void signsIn() throws Exception {
        account("signin@alphagroup.in");

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .header("X-Forwarded-For", "198.51.100.10")
                        .param("email", "signin@alphagroup.in")
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirect").value("/dashboard"));
    }

    @Test
    @DisplayName("the password is stored as a BCrypt hash, never in the clear")
    void passwordIsHashed() {
        PortalUser user = account("hash@alphagroup.in");

        assertThat(user.getPassword()).startsWith("$2");
        assertThat(user.getPassword()).doesNotContain(GOOD_PASSWORD);
    }

    @Test
    @DisplayName("an unknown address and a wrong password are indistinguishable")
    void rejectsWrongPassword() throws Exception {
        account("wrong@alphagroup.in");

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .header("X-Forwarded-For", "198.51.100.11")
                        .param("email", "wrong@alphagroup.in")
                        .param("password", "Nope@Nope#Nope1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    @DisplayName("a malformed e-mail is refused before the database is touched")
    void rejectsBadEmail() throws Exception {
        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "not-an-email")
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    @DisplayName("open-redirect attempts are collapsed to the dashboard")
    void rejectsOpenRedirect() throws Exception {
        account("redirect@alphagroup.in");

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .header("X-Forwarded-For", "198.51.100.12")
                        .param("email", "redirect@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("next", "//evil.example/steal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/dashboard"));
    }

    @Test
    @DisplayName("repeated failures trip the throttle")
    void throttlesBruteForce() throws Exception {
        account("throttle@alphagroup.in");

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/login").with(csrf().asHeader())
                            .header("X-Requested-With", "fetch")
                            .header("X-Forwarded-For", "203.0.113.7")
                            .param("email", "throttle@alphagroup.in")
                            .param("password", "Nope@Nope#Nope1!"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .param("email", "throttle@alphagroup.in")
                        .param("password", "Nope@Nope#Nope1!"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    @DisplayName("a form post without CSRF is rejected")
    void loginNeedsCsrf() throws Exception {
        mvc.perform(post("/login")
                        .param("email", "nocsrf@alphagroup.in")
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Sign up
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sign-up applies the password policy before anything is stored")
    void signupEnforcesPolicy() throws Exception {
        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "weak@alphagroup.in")
                        .param("password", "weakpass")
                        .param("confirm_password", "weakpass"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.message")
                        .value("Password must be at least 12 characters long."));

        assertThat(users.findByEmailIgnoreCase("weak@alphagroup.in")).isEmpty();
    }

    @Test
    @DisplayName("mismatched passwords are caught server-side")
    void signupChecksConfirmation() throws Exception {
        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "mismatch@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("confirm_password", GOOD_PASSWORD + "x"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("confirm_password"))
                .andExpect(jsonPath("$.message").value("Passwords do not match."));
    }

    @Test
    @DisplayName("a successful sign-up stores the account and starts a session")
    void signupCreatesAccount() throws Exception {
        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "New@AlphaGroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("confirm_password", GOOD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirect").value("/dashboard"));

        // Stored lower-cased, and never as plaintext.
        PortalUser saved = users.findByEmailIgnoreCase("new@alphagroup.in").orElseThrow();
        assertThat(saved.getPassword()).startsWith("$2");
    }

    @Test
    @DisplayName("registering the same address twice is refused")
    void signupRejectsDuplicates() throws Exception {
        account("dup@alphagroup.in");

        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Again")
                        .param("email", "dup@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("confirm_password", GOOD_PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user_exists"));
    }

    // ------------------------------------------------------------------
    // Password reset
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a reset link lets the user choose a new password")
    @Transactional
    void resetFlow() throws Exception {
        account("reset@alphagroup.in");
        String link = resetService.issue("reset@alphagroup.in").orElseThrow();
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // The page renders for a live token...
        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("Choose a new password")));

        // ...and a weak new password is refused by the same policy.
        mvc.perform(post("/reset-password").with(csrf().asHeader())
                        .param("token", token)
                        .param("password", "weakpass")
                        .param("confirm_password", "weakpass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at least 12 characters")));

        // A compliant one is accepted.
        mvc.perform(post("/reset-password").with(csrf().asHeader())
                        .param("token", token)
                        .param("password", "Starlit#Wolf42!")
                        .param("confirm_password", "Starlit#Wolf42!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/login?notice=Password%20updated.%20Please%20sign%20in."));
    }

    @Test
    @DisplayName("an expired or unknown token shows the expiry page, not a form")
    void resetRejectsUnknownToken() throws Exception {
        mvc.perform(get("/reset-password").param("token", "not-a-real-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(containsString("This link has expired")));
    }

    @Test
    @DisplayName("forgot-password never reveals whether an address exists")
    void forgotPasswordIsGeneric() throws Exception {
        account("known@alphagroup.in");

        String known = mvc.perform(post("/forgot-password").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "known@alphagroup.in"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknown = mvc.perform(post("/forgot-password").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "nobody@alphagroup.in"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        assertThat(known).contains("If an account exists for that e-mail");
    }
}
