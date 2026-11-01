package in.alphagroup.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under the {@code alpha.*} namespace in application.yml. */
@ConfigurationProperties(prefix = "alpha")
public class AlphaProperties {

    /** Shown in the page title and footer ("© 2026 Alpha Group of India"). */
    private String appName = "Alpha Group of India";

    /** Rendered as "Sessions by Spring Security". */
    private String providerName = "Spring Security";

    private String supportEmail = "help@alphagroup.in";

    /** When true, reset links are written to the log instead of e-mailed. */
    private boolean logResetLinks = false;

    private final Security security = new Security();
    private final Signup signup = new Signup();

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    public boolean isLogResetLinks() {
        return logResetLinks;
    }

    public void setLogResetLinks(boolean logResetLinks) {
        this.logResetLinks = logResetLinks;
    }

    public Security getSecurity() {
        return security;
    }

    public Signup getSignup() {
        return signup;
    }

    /** Hashing, throttling, remember-me and reset-token tuning. */
    public static class Security {
        private String rememberMeKey = "change-me-in-production";
        private int rememberMeDays = 14;
        private int loginMaxAttempts = 5;
        private int loginWindowSeconds = 900;
        private int bcryptStrength = 12;
        private int resetTokenMinutes = 60;

        public String getRememberMeKey() {
            return rememberMeKey;
        }

        public void setRememberMeKey(String rememberMeKey) {
            this.rememberMeKey = rememberMeKey;
        }

        public int getRememberMeDays() {
            return rememberMeDays;
        }

        public void setRememberMeDays(int rememberMeDays) {
            this.rememberMeDays = rememberMeDays;
        }

        public int getLoginMaxAttempts() {
            return loginMaxAttempts;
        }

        public void setLoginMaxAttempts(int loginMaxAttempts) {
            this.loginMaxAttempts = loginMaxAttempts;
        }

        public int getLoginWindowSeconds() {
            return loginWindowSeconds;
        }

        public void setLoginWindowSeconds(int loginWindowSeconds) {
            this.loginWindowSeconds = loginWindowSeconds;
        }

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }

        public int getResetTokenMinutes() {
            return resetTokenMinutes;
        }

        public void setResetTokenMinutes(int resetTokenMinutes) {
            this.resetTokenMinutes = resetTokenMinutes;
        }
    }

    /** Self-service registration switch. */
    public static class Signup {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
