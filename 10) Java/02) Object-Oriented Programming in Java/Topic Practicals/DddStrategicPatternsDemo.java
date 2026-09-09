/*
 * DddStrategicPatternsDemo.java
 *
 * Demonstrates:
 *   1. Two Bounded Contexts (Sales and Shipping) that BOTH model an "Order" concept,
 *      each with different fields and behavior appropriate to its own concern -- the
 *      same real-world word deliberately means two different things, correctly.
 *   2. Ubiquitous Language -- each context's Order uses vocabulary its own domain
 *      experts would recognize (a "pipeline stage" in Sales, a "delivery instruction"
 *      in Shipping), rather than one generic shared class with unused/misused fields.
 *   3. An Anticorruption Layer (ACL) -- a translator class that converts a Sales-context
 *      Order into a Shipping-context Order, so Shipping's own model never depends
 *      directly on Sales's internal shape.
 *   4. Context mapping in action: Shipping (downstream) is protected from Sales
 *      (upstream) changes because the translation happens in exactly one place.
 *
 * Class names are prefixed with the bounded context they belong to (Sales_ / Shipping_)
 * to stand in for what would, in a real project, be separate Java packages
 * (com.example.sales / com.example.shipping) -- mirroring the theory file's example.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/11 Domain-Driven Design Strategic Patterns.md
 *
 * Compile:  javac 03_DddStrategicPatternsDemo.java
 * Run:      java DddStrategicPatternsDemo
 */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// =============================================================================
// SALES BOUNDED CONTEXT
// Here, "Order" means a pending sales transaction -- it cares about the sales rep,
// the pipeline stage, and the discount negotiated, none of which Shipping cares about.
// =============================================================================

enum Sales_PipelineStage { NEGOTIATION, APPROVED, CLOSED_WON, CLOSED_LOST }

/** Sales context's own "Order" -- ubiquitous language for THIS context's concerns. */
class Sales_Order {
    private final String orderId;
    private final String customerId;
    private final String salesRepId;
    private final BigDecimal negotiatedTotal;
    private Sales_PipelineStage stage;
    private final List<String> lineItemSkus = new ArrayList<>();

    Sales_Order(String orderId, String customerId, String salesRepId, BigDecimal negotiatedTotal) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.salesRepId = salesRepId;
        this.negotiatedTotal = negotiatedTotal;
        this.stage = Sales_PipelineStage.NEGOTIATION;
    }

    void addSku(String sku) { lineItemSkus.add(sku); }

    void advanceTo(Sales_PipelineStage next) {   // Sales-specific business rule: no skipping backward
        if (next.ordinal() < this.stage.ordinal())
            throw new IllegalStateException("Cannot move pipeline stage backward from " + stage + " to " + next);
        this.stage = next;
    }

    String orderId() { return orderId; }
    String customerId() { return customerId; }
    String salesRepId() { return salesRepId; }
    BigDecimal negotiatedTotal() { return negotiatedTotal; }
    Sales_PipelineStage stage() { return stage; }
    List<String> lineItemSkus() { return List.copyOf(lineItemSkus); }
}

// =============================================================================
// SHIPPING BOUNDED CONTEXT
// Here, "Order" means a physical parcel to be delivered -- it cares about the delivery
// address, the carrier, and delivery instructions, none of which Sales cares about.
// Note: Shipping_Order has NO salesRepId, NO pipeline stage, NO negotiated price --
// those concepts simply do not exist in this context's ubiquitous language.
// =============================================================================

enum Shipping_Carrier { STANDARD_POST, EXPRESS_COURIER, FREIGHT }

/** Shipping context's own "Order" -- a completely different shape for the SAME word. */
class Shipping_Order {
    private final String shippingOrderId;
    private final String customerId;
    private final String deliveryAddress;
    private final Shipping_Carrier carrier;
    private final List<String> parcelSkus = new ArrayList<>();
    private String deliveryInstructions = "";
    private boolean dispatched = false;

    Shipping_Order(String shippingOrderId, String customerId, String deliveryAddress, Shipping_Carrier carrier) {
        this.shippingOrderId = shippingOrderId;
        this.customerId = customerId;
        this.deliveryAddress = deliveryAddress;
        this.carrier = carrier;
    }

    void addParcelSku(String sku) { parcelSkus.add(sku); }

    void setDeliveryInstructions(String instructions) { this.deliveryInstructions = instructions; }

    void dispatch() {   // Shipping-specific business rule: cannot dispatch an empty parcel
        if (parcelSkus.isEmpty()) throw new IllegalStateException("Cannot dispatch a shipping order with no parcels");
        this.dispatched = true;
    }

    String shippingOrderId() { return shippingOrderId; }
    String customerId() { return customerId; }
    String deliveryAddress() { return deliveryAddress; }
    Shipping_Carrier carrier() { return carrier; }
    List<String> parcelSkus() { return List.copyOf(parcelSkus); }
    String deliveryInstructions() { return deliveryInstructions; }
    boolean isDispatched() { return dispatched; }
}

// =============================================================================
// ANTICORRUPTION LAYER (ACL)
// Shipping is DOWNSTREAM of Sales: once a sale closes, Shipping needs to know about it,
// but Shipping's own model must never depend directly on Sales's internal shape --
// if Sales adds/renames a field tomorrow, only THIS translator should need to change,
// not Shipping_Order itself or any code that already works in terms of Shipping_Order.
// =============================================================================

class SalesToShippingAntiCorruptionLayer {
    private static final String DEFAULT_ADDRESS_ON_FILE = "123 Warehouse Ave";   // stands in for an address lookup

    /**
     * Translates a Sales_Order into a Shipping_Order. This is the ONLY place in the
     * entire system allowed to know about BOTH Sales_Order and Shipping_Order at once --
     * every other class works exclusively within its own bounded context.
     */
    Shipping_Order translate(Sales_Order salesOrder) {
        if (salesOrder.stage() != Sales_PipelineStage.CLOSED_WON) {
            throw new IllegalStateException(
                    "Refusing to translate a Sales_Order that has not reached CLOSED_WON (was: " + salesOrder.stage() + ")");
        }

        // Sales has no notion of "carrier" at all -- the ACL decides a sensible default,
        // exactly the kind of translation decision an ACL exists to encapsulate.
        Shipping_Carrier carrier = salesOrder.negotiatedTotal().compareTo(BigDecimal.valueOf(1000)) >= 0
                ? Shipping_Carrier.EXPRESS_COURIER
                : Shipping_Carrier.STANDARD_POST;

        Shipping_Order shippingOrder = new Shipping_Order(
                "SHIP-" + salesOrder.orderId(),   // Shipping mints its OWN identifier, not reusing Sales's
                salesOrder.customerId(),
                DEFAULT_ADDRESS_ON_FILE,
                carrier);

        for (String sku : salesOrder.lineItemSkus()) {
            shippingOrder.addParcelSku(sku);       // "line item" (Sales language) becomes "parcel" (Shipping language)
        }

        shippingOrder.setDeliveryInstructions("Handle with care -- rep " + salesOrder.salesRepId());
        return shippingOrder;
    }
}

// =============================================================================
// Main class
// =============================================================================

public class DddStrategicPatternsDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
    }

    // -------------------------------------------------------------------------
    // Demo 1: Same word, "Order", two totally different shapes in two contexts.
    // -------------------------------------------------------------------------
    private static void demoTwoBoundedContexts() {
        printSection("1) Two Bounded Contexts -- Sales_Order and Shipping_Order model \"Order\" differently");

        Sales_Order salesOrder = new Sales_Order("S-100", "cust-7", "rep-9", BigDecimal.valueOf(1500));
        salesOrder.addSku("SKU-AAA");
        salesOrder.addSku("SKU-BBB");
        System.out.println("  Sales_Order fields:    orderId=" + salesOrder.orderId()
                + ", salesRepId=" + salesOrder.salesRepId() + ", stage=" + salesOrder.stage());

        Shipping_Order shippingOrder = new Shipping_Order("SHIP-100", "cust-7", "456 Main St", Shipping_Carrier.STANDARD_POST);
        shippingOrder.addParcelSku("SKU-AAA");
        System.out.println("  Shipping_Order fields: shippingOrderId=" + shippingOrder.shippingOrderId()
                + ", carrier=" + shippingOrder.carrier() + ", deliveryAddress=" + shippingOrder.deliveryAddress());

        System.out.println("  NOTE: Shipping_Order has no salesRepId/pipeline stage; Sales_Order has no carrier/address --");
        System.out.println("        each model is complete and correct only WITHIN its own bounded context.");
    }

    // -------------------------------------------------------------------------
    // Demo 2: Sales-specific business rule -- pipeline stage cannot move backward.
    // -------------------------------------------------------------------------
    private static void demoSalesPipelineRule() {
        printSection("2) Ubiquitous language + a Sales-specific rule -- pipeline stage cannot regress");

        Sales_Order order = new Sales_Order("S-101", "cust-8", "rep-1", BigDecimal.valueOf(500));
        order.advanceTo(Sales_PipelineStage.APPROVED);
        order.advanceTo(Sales_PipelineStage.CLOSED_WON);
        System.out.println("  Final stage: " + order.stage());

        try {
            order.advanceTo(Sales_PipelineStage.NEGOTIATION);   // moving backward -- rejected
        } catch (IllegalStateException e) {
            System.out.println("  Caught expected exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Demo 3: Anticorruption Layer translating a closed Sales_Order into Shipping's model.
    // -------------------------------------------------------------------------
    private static void demoAnticorruptionLayer() {
        printSection("3) Anticorruption Layer -- translating Sales_Order into Shipping_Order");

        Sales_Order salesOrder = new Sales_Order("S-102", "cust-9", "rep-3", BigDecimal.valueOf(1200));
        salesOrder.addSku("SKU-CCC");
        salesOrder.addSku("SKU-DDD");
        salesOrder.advanceTo(Sales_PipelineStage.APPROVED);
        salesOrder.advanceTo(Sales_PipelineStage.CLOSED_WON);

        SalesToShippingAntiCorruptionLayer acl = new SalesToShippingAntiCorruptionLayer();
        Shipping_Order translated = acl.translate(salesOrder);

        System.out.println("  Translated shippingOrderId: " + translated.shippingOrderId());
        System.out.println("  Translated carrier (>= 1000 total -> EXPRESS_COURIER): " + translated.carrier());
        System.out.println("  Translated parcels: " + translated.parcelSkus());
        System.out.println("  Translated instructions: " + translated.deliveryInstructions());

        assert translated.carrier() == Shipping_Carrier.EXPRESS_COURIER;
        assert translated.parcelSkus().size() == 2;

        translated.dispatch();
        assert translated.isDispatched();
    }

    // -------------------------------------------------------------------------
    // Demo 4: The ACL guards Shipping from Sales orders that are not ready yet --
    // Shipping's rules are never bypassed just because Sales's data exists.
    // -------------------------------------------------------------------------
    private static void demoAclGuardsAgainstPrematureTranslation() {
        printSection("4) ACL guard -- refuses to translate a Sales_Order still in negotiation");

        Sales_Order stillNegotiating = new Sales_Order("S-103", "cust-10", "rep-4", BigDecimal.valueOf(300));
        SalesToShippingAntiCorruptionLayer acl = new SalesToShippingAntiCorruptionLayer();

        try {
            acl.translate(stillNegotiating);
        } catch (IllegalStateException e) {
            System.out.println("  Caught expected exception: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        demoTwoBoundedContexts();
        demoSalesPipelineRule();
        demoAnticorruptionLayer();
        demoAclGuardsAgainstPrematureTranslation();

        System.out.println();
        System.out.println("All DDD Strategic Patterns demos completed.");
    }
}
