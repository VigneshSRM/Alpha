package in.alphagroup.portal.policy;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every dialling code the sign-up form offers.
 *
 * <p>The table is {@code iso|dial|name|min|max}, one country per line, ordered
 * by display name. {@code min}/{@code max} bound the national significant
 * number — the part dialled after the country code — and come from each
 * country's numbering plan. Where a plan is open-ended the entry carries the
 * E.164 envelope (6–14 digits) instead of an invented exact length.
 *
 * <p>This class is the single source of truth: the template iterates
 * {@link #ALL} and {@link PhoneNumbers} validates against it, so the list the
 * user picks from and the list the server accepts cannot drift apart.
 */
public final class Countries {

    /** Default selection on a fresh form. */
    public static final String DEFAULT_ISO = "IN";

    private static final String TABLE = """
            AF|93|Afghanistan|9|9
            AL|355|Albania|9|9
            DZ|213|Algeria|9|9
            AS|1684|American Samoa|7|7
            AD|376|Andorra|6|9
            AO|244|Angola|9|9
            AI|1264|Anguilla|7|7
            AG|1268|Antigua and Barbuda|7|7
            AR|54|Argentina|10|10
            AM|374|Armenia|8|8
            AW|297|Aruba|7|7
            AU|61|Australia|9|9
            AT|43|Austria|10|11
            AZ|994|Azerbaijan|9|9
            BS|1242|Bahamas|7|7
            BH|973|Bahrain|8|8
            BD|880|Bangladesh|10|10
            BB|1246|Barbados|7|7
            BY|375|Belarus|9|9
            BE|32|Belgium|9|9
            BZ|501|Belize|7|7
            BJ|229|Benin|8|8
            BM|1441|Bermuda|7|7
            BT|975|Bhutan|8|8
            BO|591|Bolivia|8|8
            BA|387|Bosnia and Herzegovina|8|8
            BW|267|Botswana|8|8
            BR|55|Brazil|10|11
            IO|246|British Indian Ocean Territory|7|7
            VG|1284|British Virgin Islands|7|7
            BN|673|Brunei|7|7
            BG|359|Bulgaria|9|9
            BF|226|Burkina Faso|8|8
            BI|257|Burundi|8|8
            KH|855|Cambodia|8|9
            CM|237|Cameroon|9|9
            CA|1|Canada|10|10
            CV|238|Cape Verde|7|7
            KY|1345|Cayman Islands|7|7
            CF|236|Central African Republic|8|8
            TD|235|Chad|8|8
            CL|56|Chile|9|9
            CN|86|China|11|11
            CO|57|Colombia|10|10
            KM|269|Comoros|7|7
            CG|242|Congo - Brazzaville|9|9
            CD|243|Congo - Kinshasa|9|9
            CK|682|Cook Islands|5|5
            CR|506|Costa Rica|8|8
            CI|225|Côte d’Ivoire|10|10
            HR|385|Croatia|8|9
            CU|53|Cuba|8|8
            CW|599|Curaçao|7|8
            CY|357|Cyprus|8|8
            CZ|420|Czechia|9|9
            DK|45|Denmark|8|8
            DJ|253|Djibouti|8|8
            DM|1767|Dominica|7|7
            DO|1809|Dominican Republic|10|10
            EC|593|Ecuador|9|9
            EG|20|Egypt|10|10
            SV|503|El Salvador|8|8
            GQ|240|Equatorial Guinea|9|9
            ER|291|Eritrea|7|7
            EE|372|Estonia|7|8
            SZ|268|Eswatini|8|8
            ET|251|Ethiopia|9|9
            FK|500|Falkland Islands|5|5
            FO|298|Faroe Islands|6|6
            FJ|679|Fiji|7|7
            FI|358|Finland|9|10
            FR|33|France|9|9
            GF|594|French Guiana|9|9
            PF|689|French Polynesia|8|8
            GA|241|Gabon|8|8
            GM|220|Gambia|7|7
            GE|995|Georgia|9|9
            DE|49|Germany|10|11
            GH|233|Ghana|9|9
            GI|350|Gibraltar|8|8
            GR|30|Greece|10|10
            GL|299|Greenland|6|6
            GD|1473|Grenada|7|7
            GP|590|Guadeloupe|9|9
            GU|1671|Guam|7|7
            GT|502|Guatemala|8|8
            GG|44|Guernsey|10|10
            GN|224|Guinea|9|9
            GW|245|Guinea-Bissau|9|9
            GY|592|Guyana|7|7
            HT|509|Haiti|8|8
            HN|504|Honduras|8|8
            HK|852|Hong Kong SAR China|8|8
            HU|36|Hungary|9|9
            IS|354|Iceland|7|7
            IN|91|India|10|10
            ID|62|Indonesia|9|12
            IR|98|Iran|10|10
            IQ|964|Iraq|10|10
            IE|353|Ireland|9|9
            IM|44|Isle of Man|10|10
            IL|972|Israel|9|9
            IT|39|Italy|9|10
            JM|1876|Jamaica|7|7
            JP|81|Japan|10|10
            JE|44|Jersey|10|10
            JO|962|Jordan|9|9
            KZ|7|Kazakhstan|10|10
            KE|254|Kenya|9|9
            KI|686|Kiribati|5|8
            KW|965|Kuwait|8|8
            KG|996|Kyrgyzstan|9|9
            LA|856|Laos|8|10
            LV|371|Latvia|8|8
            LB|961|Lebanon|7|8
            LS|266|Lesotho|8|8
            LR|231|Liberia|8|9
            LY|218|Libya|9|9
            LI|423|Liechtenstein|7|7
            LT|370|Lithuania|8|8
            LU|352|Luxembourg|9|9
            MO|853|Macao SAR China|8|8
            MG|261|Madagascar|9|9
            MW|265|Malawi|9|9
            MY|60|Malaysia|9|10
            MV|960|Maldives|7|7
            ML|223|Mali|8|8
            MT|356|Malta|8|8
            MH|692|Marshall Islands|7|7
            MQ|596|Martinique|9|9
            MR|222|Mauritania|8|8
            MU|230|Mauritius|8|8
            YT|262|Mayotte|9|9
            MX|52|Mexico|10|10
            FM|691|Micronesia|7|7
            MD|373|Moldova|8|8
            MC|377|Monaco|8|9
            MN|976|Mongolia|8|8
            ME|382|Montenegro|8|8
            MS|1664|Montserrat|7|7
            MA|212|Morocco|9|9
            MZ|258|Mozambique|9|9
            MM|95|Myanmar (Burma)|8|10
            NA|264|Namibia|9|9
            NR|674|Nauru|7|7
            NP|977|Nepal|10|10
            NL|31|Netherlands|9|9
            NC|687|New Caledonia|6|6
            NZ|64|New Zealand|8|10
            NI|505|Nicaragua|8|8
            NE|227|Niger|8|8
            NG|234|Nigeria|10|10
            NU|683|Niue|4|4
            NF|672|Norfolk Island|6|6
            MK|389|North Macedonia|8|8
            MP|1670|Northern Mariana Islands|7|7
            NO|47|Norway|8|8
            OM|968|Oman|8|8
            PK|92|Pakistan|10|10
            PW|680|Palau|7|7
            PS|970|Palestine|9|9
            PA|507|Panama|8|8
            PG|675|Papua New Guinea|8|8
            PY|595|Paraguay|9|9
            PE|51|Peru|9|9
            PH|63|Philippines|10|10
            PL|48|Poland|9|9
            PT|351|Portugal|9|9
            PR|1787|Puerto Rico|10|10
            QA|974|Qatar|8|8
            RE|262|Réunion|9|9
            RO|40|Romania|9|9
            RU|7|Russia|10|10
            RW|250|Rwanda|9|9
            WS|685|Samoa|7|7
            SM|378|San Marino|8|10
            ST|239|São Tomé and Príncipe|7|7
            SA|966|Saudi Arabia|9|9
            SN|221|Senegal|9|9
            RS|381|Serbia|9|9
            SC|248|Seychelles|7|7
            SL|232|Sierra Leone|8|8
            SG|65|Singapore|8|8
            SX|1721|Sint Maarten|7|7
            SK|421|Slovakia|9|9
            SI|386|Slovenia|8|8
            SB|677|Solomon Islands|5|7
            SO|252|Somalia|8|9
            ZA|27|South Africa|9|9
            KR|82|South Korea|9|10
            SS|211|South Sudan|9|9
            ES|34|Spain|9|9
            LK|94|Sri Lanka|9|9
            BL|590|St Barthélemy|9|9
            SH|290|St Helena|4|5
            KN|1869|St Kitts and Nevis|7|7
            LC|1758|St Lucia|7|7
            MF|590|St Martin|9|9
            PM|508|St Pierre and Miquelon|6|6
            VC|1784|St Vincent and the Grenadines|7|7
            SD|249|Sudan|9|9
            SR|597|Suriname|7|7
            SE|46|Sweden|9|9
            CH|41|Switzerland|9|9
            SY|963|Syria|9|9
            TW|886|Taiwan|9|9
            TJ|992|Tajikistan|9|9
            TZ|255|Tanzania|9|9
            TH|66|Thailand|9|9
            TL|670|Timor-Leste|8|8
            TG|228|Togo|8|8
            TK|690|Tokelau|4|4
            TO|676|Tonga|5|7
            TT|1868|Trinidad and Tobago|7|7
            TN|216|Tunisia|8|8
            TR|90|Türkiye|10|10
            TM|993|Turkmenistan|8|8
            TC|1649|Turks and Caicos Islands|7|7
            TV|688|Tuvalu|5|6
            UG|256|Uganda|9|9
            UA|380|Ukraine|9|9
            AE|971|United Arab Emirates|9|9
            GB|44|United Kingdom|10|10
            US|1|United States|10|10
            UY|598|Uruguay|8|8
            UZ|998|Uzbekistan|9|9
            VU|678|Vanuatu|5|7
            VA|39|Vatican City|9|10
            VE|58|Venezuela|10|10
            VN|84|Vietnam|9|9
            VI|1340|US Virgin Islands|7|7
            WF|681|Wallis and Futuna|6|6
            EH|212|Western Sahara|9|9
            YE|967|Yemen|9|9
            ZM|260|Zambia|9|9
            ZW|263|Zimbabwe|9|9
            """;

    /** Every entry, ordered by display name. */
    public static final List<Country> ALL;

    private static final Map<String, Country> BY_ISO;

    static {
        Map<String, Country> byIso = new LinkedHashMap<>();
        List<Country> all = TABLE.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(Countries::parse)
                .sorted(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .toList();
        for (Country country : all) {
            byIso.put(country.iso(), country);
        }
        ALL = all;
        BY_ISO = Map.copyOf(byIso);
    }

    private static Country parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalStateException("bad country row: " + line);
        }
        return new Country(
                parts[0].trim(),
                "+" + parts[1].trim(),
                parts[2].trim(),
                Integer.parseInt(parts[3].trim()),
                Integer.parseInt(parts[4].trim()));
    }

    /** Look up by ISO code; null when unknown. */
    public static Country byIso(String iso) {
        if (iso == null) {
            return null;
        }
        return BY_ISO.get(iso.trim().toUpperCase(Locale.ROOT));
    }

    /** Where a number should default to when the browser sends nothing. */
    public static Country defaultCountry() {
        Country country = byIso(DEFAULT_ISO);
        if (country == null) {
            throw new IllegalStateException("default country missing from table");
        }
        return country;
    }

    private Countries() {
    }
}
