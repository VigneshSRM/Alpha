package in.alphagroup.portal.security;

import in.alphagroup.portal.config.AlphaProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter for failed sign-ins, keyed by e-mail *and*
 * client address so neither a single account nor a single attacker can be
 * hammered.
 *
 * <p>Deliberately per-process and dependency-free: it is a speed bump, not an
 * account lockout. For a multi-instance deployment swap this bean for a Redis
 * or Bucket4j implementation — the call sites do not change.
 */
@Component
public class LoginThrottle {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public LoginThrottle(AlphaProperties properties) {
        this.maxAttempts = Math.max(1, properties.getSecurity().getLoginMaxAttempts());
        this.windowMillis = Math.max(1, properties.getSecurity().getLoginWindowSeconds()) * 1000L;
    }

    /** Seconds the caller must wait, or {@code 0} when it may proceed. */
    public synchronized int retryAfter(List<String> keys) {
        long now = System.currentTimeMillis();
        for (String key : keys) {
            Deque<Long> bucket = pruned(key, now);
            if (bucket.size() >= maxAttempts) {
                long waitMillis = bucket.peekFirst() + windowMillis - now;
                return Math.max(1, (int) (waitMillis / 1000L) + 1);
            }
        }
        return 0;
    }

    /** Records a failure and returns how many attempts remain for the first key. */
    public synchronized int registerFailure(List<String> keys) {
        long now = System.currentTimeMillis();
        for (String key : keys) {
            pruned(key, now).addLast(now);
        }
        Deque<Long> bucket = pruned(keys.get(0), now);
        return Math.max(0, maxAttempts - bucket.size());
    }

    public synchronized void reset(List<String> keys) {
        for (String key : keys) {
            hits.remove(key);
        }
    }

    private Deque<Long> pruned(String key, long now) {
        Deque<Long> bucket = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!bucket.isEmpty() && now - bucket.peekFirst() > windowMillis) {
            bucket.pollFirst();
        }
        return bucket;
    }
}
