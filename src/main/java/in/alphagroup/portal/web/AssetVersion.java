package in.alphagroup.portal.web;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Cache-busting fingerprint for {@code app.css} / {@code auth.js}.
 *
 * <p>Templates append {@code ?v=…} so a stylesheet change is never hidden behind
 * a stale 304. Computed once at startup from the packaged copies.
 */
@Component
public class AssetVersion {

    private static final Logger log = LoggerFactory.getLogger(AssetVersion.class);
    private static final String[] ASSETS = {"css/app.css", "js/auth.js", "js/dashboard.js"};

    private final String version;

    public AssetVersion() {
        this.version = compute();
    }

    public String getVersion() {
        return version;
    }

    private String compute() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            boolean any = false;
            for (String asset : ASSETS) {
                ClassPathResource resource = new ClassPathResource("static/" + asset);
                if (!resource.exists()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    digest.update(in.readAllBytes());
                    any = true;
                }
            }
            if (!any) {
                return "dev";
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.substring(0, 10);
        } catch (Exception ex) {
            log.debug("Asset fingerprint unavailable, defaulting to 'dev': {}", ex.toString());
            return "dev";
        }
    }
}
