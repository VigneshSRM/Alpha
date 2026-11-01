package in.alphagroup.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under the {@code alpha.*} namespace in application.yml. */
@ConfigurationProperties(prefix = "alpha")
public class AlphaProperties {

    /** Shown in the page title and footer ("© 2026 Alpha Group of India"). */
    private String appName = "Alpha Group of India";

    /** Rendered as "Sessions by Supabase Auth". */
    private String providerName = "Supabase";

    private String supportEmail = "help@alphagroup.in";

    private final Supabase supabase = new Supabase();
    private final Security security = new Security();

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

    public Supabase getSupabase() {
        return supabase;
    }

    public Security getSecurity() {
        return security;
    }

    /** Supabase project settings — the anon key only, never the service role. */
    public static class Supabase {
        /** e.g. https://abcdefghijkl.supabase.co */
        private String url = "";
        private String anonKey = "";
        private int timeoutSeconds = 15;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAnonKey() {
            return anonKey;
        }

        public void setAnonKey(String anonKey) {
            this.anonKey = anonKey;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** Brute-force protection and session tuning. */
    public static class Security {
        private int tokenRefreshSkewSeconds = 60;
        private int loginMaxAttempts = 5;
        private int loginWindowSeconds = 900;
        private int rememberMeDays = 14;

        public int getTokenRefreshSkewSeconds() {
            return tokenRefreshSkewSeconds;
        }

        public void setTokenRefreshSkewSeconds(int value) {
            this.tokenRefreshSkewSeconds = value;
        }

        public int getLoginMaxAttempts() {
            return loginMaxAttempts;
        }

        public void setLoginMaxAttempts(int value) {
            this.loginMaxAttempts = value;
        }

        public int getLoginWindowSeconds() {
            return loginWindowSeconds;
        }

        public void setLoginWindowSeconds(int value) {
            this.loginWindowSeconds = value;
        }

        public int getRememberMeDays() {
            return rememberMeDays;
        }

        public void setRememberMeDays(int value) {
            this.rememberMeDays = value;
        }
    }
}
