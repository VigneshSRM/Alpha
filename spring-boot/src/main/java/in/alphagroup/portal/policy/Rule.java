package in.alphagroup.portal.policy;

/**
 * One user-facing password requirement.
 *
 * @param key   stable identifier used by the checklist DOM ({@code data-rule})
 * @param label bold line of the checklist
 * @param hint  short grey tail rendered inside {@code <em>}
 */
public record Rule(String key, String label, String hint) {
}
