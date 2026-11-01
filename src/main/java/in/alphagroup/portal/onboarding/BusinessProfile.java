package in.alphagroup.portal.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The business behind a portal account — captured once, right after
 * registration, and editable from Settings later.
 */
@Entity
@Table(name = "business_profile")
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String vertical;

    @Column(name = "business_name", nullable = false, length = 120)
    private String businessName;

    /** E.164, e.g. {@code +919876543210}. */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "business_type", nullable = false, length = 60)
    private String businessType;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(length = 15)
    private String gstin;

    /** Fourteen digits, restaurants only, optional. */
    @Column(length = 14)
    private String fssai;

    @Column(name = "fssai_expiry")
    private LocalDate fssaiExpiry;

    @Column(name = "address_line1", nullable = false, length = 160)
    private String addressLine1;

    @Column(name = "address_line2", length = 160)
    private String addressLine2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "time_zone", nullable = false, length = 40)
    private String timeZone;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "fiscal_year_start", nullable = false)
    private int fiscalYearStart;

    @Column(name = "inventory_start_date", nullable = false)
    private LocalDate inventoryStartDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessProfile() {
        // for JPA
    }

    public BusinessProfile(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getVertical() {
        return vertical;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getPhone() {
        return phone;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getCountry() {
        return country;
    }

    public String getState() {
        return state;
    }

    public String getGstin() {
        return gstin;
    }

    public String getFssai() {
        return fssai;
    }

    public LocalDate getFssaiExpiry() {
        return fssaiExpiry;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getPincode() {
        return pincode;
    }

    public String getLanguage() {
        return language;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getCurrency() {
        return currency;
    }

    public int getFiscalYearStart() {
        return fiscalYearStart;
    }

    public LocalDate getInventoryStartDate() {
        return inventoryStartDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setVertical(String vertical) {
        this.vertical = vertical;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public void setFssai(String fssai) {
        this.fssai = fssai;
    }

    public void setFssaiExpiry(LocalDate fssaiExpiry) {
        this.fssaiExpiry = fssaiExpiry;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setFiscalYearStart(int fiscalYearStart) {
        this.fiscalYearStart = fiscalYearStart;
    }

    public void setInventoryStartDate(LocalDate inventoryStartDate) {
        this.inventoryStartDate = inventoryStartDate;
    }
}
