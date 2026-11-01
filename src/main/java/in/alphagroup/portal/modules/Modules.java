package in.alphagroup.portal.modules;

import java.util.List;
import java.util.Optional;

/**
 * The modules an account can switch on. The order here is the order on screen
 * and the order they are stored in.
 */
public final class Modules {

    /** One card on the picker. */
    public record Module(String code, String name, String blurb) {
    }

    public static final List<Module> ALL = List.of(
            new Module("pos", "POS", "Bill at the counter or at the table."),
            new Module("sales", "Sales", "Orders, returns and the day's takings."),
            new Module("inventory", "Inventory", "Stock on hand, batches and transfers."),
            new Module("purchase", "Purchase", "Purchase orders and supplier bills."),
            new Module("crm", "Alpha and Luna Customers (CRM)", "Profiles, loyalty and follow-ups."),
            new Module("documents", "Documents", "Contracts, licences and scanned copies."),
            new Module("templates", "Templates", "Invoices, receipts and letterheads."),
            new Module("customization", "Customization", "Fields, numbering and your branding."),
            new Module("reports", "Reports", "Sales, stock and tax, by period."),
            new Module("settings", "Settings", "Users, taxes and preferences."));

    public static Optional<Module> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return ALL.stream().filter(module -> module.code().equals(code)).findFirst();
    }

    public static boolean isKnown(String code) {
        return byCode(code).isPresent();
    }

    private Modules() {
    }
}
