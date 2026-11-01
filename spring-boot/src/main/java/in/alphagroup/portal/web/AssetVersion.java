package in.alphagroup.portal.web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Cache-busting fingerprint for {@code app.css} / {@code auth.js}.
 *
 * <p>Templates append {@code ?v=...} so a stylesheet change is never hidden
 * behind a stale 304 — the same guarantee the Flask build gets from
 * {@code asset_v}. The digest is computed once at startup from whichever copy is
 * in play: the jar when packaged, the repository when running {@code dev}.
 */
@Component
public class AssetVersion {

    private static final Logger log = LoggerFactory.getLogger(AssetVersion.class);
    private static final String[] ASSETS = {"css/app.css", "js/auth.js"};

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
                byte[] bytes = read("static/" + asset, Path.of("..", "app", "static", asset));
                if (bytes != null) {
                    digest.update(bytes);
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

    private byte[] read(String classpathLocation, Path diskPath) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return in.readAllBytes();
        } catch (Exception ignored) {
            // Not packaged yet — fall through to the repository copy.
        }
        try {
            if (Files.isReadable(diskPath)) {
                return Files.readString(diskPath, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Nothing else to try.
        }
        return null;
    }
}
