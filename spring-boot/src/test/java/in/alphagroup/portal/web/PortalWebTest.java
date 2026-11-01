package in.alphagroup.portal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import in.alphagroup.portal.auth.AuthFailureException;
import in.alphagroup.portal.auth.AuthOutcome;
import in.alphagroup.portal.auth.AuthSession;
import in.alphagroup.portal.auth.SupabaseAuthClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP behaviour with Supabase mocked out.
 *
 * <p>Covers what the port must not regress: the page renders the "!" indicator
 * and the logo inside the panel, CSRF is enforced, the policy verdict comes from
 * the server, and the brute-force throttle answers 429.
 */
@SpringBootTest(properties = {
        "alpha.security.login-max-attempts=5",
        "alpha.security.login-window-seconds=60"
})
@AutoConfigureMockMvc
class PortalWebTest {

    private static final String GOOD_PASSWORD = "Wolf@Runs#Dawn27!";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupabaseAuthClient client;

    @BeforeEach
    void setUp() {
        lenient().when(client.supportsSignup()).thenReturn(true);
        lenient().when(client.supportsPasswordReset()).thenReturn(true);
    }

    private static AuthSession session() {
        return new AuthSession(
                "access-token",
                "refresh-token",
                System.currentTimeMillis() / 1000L + 3600,
                Map.of("id", "8f14e45f-ceea-467a-9a3b-1f0c2a5e7d66",
                        "email", "ada@alphagroup.in",
                        "user_metadata", Map.of("full_name", "Ada Lovelace")));
    }

    private static AuthFailureException invalidCredentials() {
        return new AuthFailureException(
                "Invalid e-mail or password. Please try again.",
                "invalid_credentials",
                HttpStatus.UNAUTHORIZED,
                null);
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
                // The retired strapline and the old caption must stay gone.
                .andExpect(content().string(not(containsString("The Way to Lead"))))
                .andExpect(content().string(not(containsString("12+ characters · upper"))));
    }

    @Test
    @DisplayName("footer is the short copyright plus provider credit")
    void footerCopy() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("Sessions by Supabase Auth")));
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
                .andExpect(jsonPath("$.empty").value(false))
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
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.valid").value(false));
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
        given(client.signIn(eq("ada@alphagroup.in"), eq(GOOD_PASSWORD)))
                .willReturn(AuthOutcome.signedIn(session()));

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "ada@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .header("X-Forwarded-For", "198.51.100.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirect").value("/dashboard"));
    }

    @Test
    @DisplayName("open-redirect attempts are collapsed to the dashboard")
    void rejectsOpenRedirect() throws Exception {
        given(client.signIn(eq("ada@alphagroup.in"), eq(GOOD_PASSWORD)))
                .willReturn(AuthOutcome.signedIn(session()));

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "ada@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("next", "//evil.example/steal")
                        .header("X-Forwarded-For", "198.51.100.11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/dashboard"));
    }

    @Test
    @DisplayName("a malformed e-mail is refused before the provider is called")
    void rejectsBadEmail() throws Exception {
        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "not-an-email")
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    @DisplayName("provider rejection becomes a generic 401, not an enumeration hint")
    void rejectsWrongPassword() throws Exception {
        willThrow(invalidCredentials())
                .given(client).signIn(eq("bad@alphagroup.in"), anyString());

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", "bad@alphagroup.in")
                        .param("password", "Nope@Nope#Nope1!")
                        .header("X-Forwarded-For", "198.51.100.9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid e-mail or password. Please try again."));
    }

    @Test
    @DisplayName("repeated failures trip the throttle")
    void throttlesBruteForce() throws Exception {
        String email = "throttle@alphagroup.in";
        willThrow(invalidCredentials()).given(client).signIn(eq(email), anyString());

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/login").with(csrf().asHeader())
                            .header("X-Requested-With", "fetch")
                            .param("email", email)
                            .param("password", "Nope@Nope#Nope1!")
                            .header("X-Forwarded-For", "203.0.113.7"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/login").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("email", email)
                        .param("password", "Nope@Nope#Nope1!")
                        .header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    @DisplayName("a form post without CSRF is rejected")
    void loginNeedsCsrf() throws Exception {
        mvc.perform(post("/login")
                        .param("email", "ada@alphagroup.in")
                        .param("password", GOOD_PASSWORD))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Sign up
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sign-up applies the password policy before calling Supabase")
    void signupEnforcesPolicy() throws Exception {
        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "ada@alphagroup.in")
                        .param("password", "weakpass")
                        .param("confirm_password", "weakpass"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.message")
                        .value("Password must be at least 12 characters long."));
    }

    @Test
    @DisplayName("mismatched passwords are caught server-side")
    void signupChecksConfirmation() throws Exception {
        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "ada@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("confirm_password", GOOD_PASSWORD + "x"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field").value("confirm_password"))
                .andExpect(jsonPath("$.message").value("Passwords do not match."));
    }

    @Test
    @DisplayName("a successful sign-up starts a session straight away")
    void signupSignsIn() throws Exception {
        given(client.signUp(eq("new@alphagroup.in"), eq(GOOD_PASSWORD), eq("Ada Lovelace")))
                .willReturn(new AuthOutcome(session(), "Welcome to Alpha Group of India.", false));

        mvc.perform(post("/signup").with(csrf().asHeader())
                        .header("X-Requested-With", "fetch")
                        .param("name", "Ada Lovelace")
                        .param("email", "new@alphagroup.in")
                        .param("password", GOOD_PASSWORD)
                        .param("confirm_password", GOOD_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirect").value("/dashboard"));
    }

    // ------------------------------------------------------------------
    // Health
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the health probe is public")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
