package in.alphagroup.portal.auth;

/**
 * Result of a successful call to the identity provider.
 *
 * @param session           null when the provider did not hand back a session
 *                          (e.g. sign-up awaiting e-mail confirmation)
 * @param message           user-facing sentence
 * @param needsConfirmation true when the account must confirm its e-mail first
 */
public record AuthOutcome(AuthSession session, String message, boolean needsConfirmation) {

    public static AuthOutcome signedIn(AuthSession session) {
        return new AuthOutcome(session, "Signed in.", false);
    }

    public static AuthOutcome awaitingConfirmation(String message) {
        return new AuthOutcome(null, message, true);
    }
}
