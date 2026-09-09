package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.model.ModularReport;
import com.chaykin.jasper.core.model.SubreportModule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Test fixtures for {@link JasperModularDataFillerTest}.
 * Contains minimal report and subreport classes that simulate real-world usage.
 */
@SuppressWarnings("unused") // fixture fields are read reflectively by JasperModularDataFiller
final class JasperModularDataFillerFixture {

    private JasperModularDataFillerFixture() {}

    static class LineItem {

        String name;
        BigDecimal amount;

        LineItem(String name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }

    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class ItemsModule extends SubreportModule {

        String title;

        ItemsModule(String title) {this.title = title;}

        @Override
        public boolean isEmpty() {return false;}

    }

    static class SpecialItemsModule extends ItemsModule {

        SpecialItemsModule(String title) {super(title);}
    }

    @JasperModularReport(templatePath = "/reports/subreport.jrxml")
    static class BaseTypedSubreportReport extends ModularReport {

        SubreportModule section;

        BaseTypedSubreportReport(SubreportModule section) {this.section = section;}
    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class PojoModule {

        String title = "not a module";
    }

    @JasperModularReport(templatePath = "/reports/subreport.jrxml")
    static class PojoSubreportReport extends ModularReport {

        PojoModule pojoModule = new PojoModule();
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class PojoListReport extends ModularReport {

        List<PojoModule> modules = List.of(new PojoModule());
    }

    @JasperSubreport(templatePath = "/reports/other.jrxml")
    static class OtherModule extends SubreportModule {

        String note;

        OtherModule(String note) {this.note = note;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperModularReport(templatePath = "/reports/scalar.jrxml")
    static class ScalarReport extends ModularReport {

        String customerName;
        BigDecimal total;

        ScalarReport(String customerName, BigDecimal total) {
            this.customerName = customerName;
            this.total = total;
        }
    }

    @JasperModularReport(templatePath = "/reports/static_fields.jrxml")
    static class StaticFieldReport extends ModularReport {

        static final String STATIC_CONSTANT = "shared";

        @JasperIgnore
        static final String IGNORED_CONSTANT = "hidden";

        String instanceField = "kept";
    }

    @JasperModularReport(templatePath = "/reports/nullable.jrxml")
    static class NullableReport extends ModularReport {

        String present = "hello";
        String absent = null;

    }

    @JasperModularReport(templatePath = "/reports/ignored.jrxml")
    static class IgnoredFieldReport extends ModularReport {

        String visible = "yes";

        @JasperIgnore
        String hidden = "no";

    }

    @JasperModularReport(templatePath = "/reports/collection.jrxml")
    static class CollectionReport extends ModularReport {

        List<LineItem> items;

        CollectionReport(List<LineItem> items) {this.items = items;}

    }

    @JasperModularReport(templatePath = "/reports/subreport.jrxml")
    static class SubreportReport extends ModularReport {

        ItemsModule itemsModule;

        SubreportReport(ItemsModule module) {this.itemsModule = module;}

    }

    @JasperModularReport(templatePath = "/reports/prefix.jrxml")
    static class NoPrefixReport extends ModularReport {

        OtherModule otherModule;

        NoPrefixReport(OtherModule module) {this.otherModule = module;}
    }

    @JasperModularReport(templatePath = "/reports/mixed.jrxml")
    static class MixedReport extends ModularReport {

        String title;
        List<LineItem> items;
        ItemsModule itemsModule;

        MixedReport(String title, List<LineItem> items, ItemsModule module) {
            this.title = title;
            this.items = items;
            this.itemsModule = module;
        }
    }

    static class ParentReport extends ModularReport {

        String parentField = "fromParent";

    }

    @JasperModularReport(templatePath = "/reports/child.jrxml")
    static class ChildReport extends ParentReport {

        String childField = "fromChild";

    }

    record RecordItem(String name) {

    }

    @JasperModularReport(templatePath = "/reports/collection.jrxml")
    static class RecordListReport extends ModularReport {

        List<RecordItem> items;

        RecordListReport(List<RecordItem> items) {this.items = items;}
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class WildcardListReport extends ModularReport {

        List<? extends ItemsModule> modules;

        WildcardListReport(List<? extends ItemsModule> modules) {this.modules = modules;}
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class SubreportListReport extends ModularReport {

        List<ItemsModule> modules;

        SubreportListReport(List<ItemsModule> modules) {this.modules = modules;}

    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class SelfNodeModule extends SubreportModule {

        List<SelfNodeModule> children;

        SelfNodeModule(List<SelfNodeModule> children) {this.children = children;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperModularReport(templatePath = "/reports/multi.jrxml")
    static class MultiSubreportReport extends ModularReport {

        ItemsModule itemsModule;
        OtherModule otherModule;

        MultiSubreportReport(ItemsModule items, OtherModule other) {
            this.itemsModule = items;
            this.otherModule = other;
        }

    }

    @JasperModularReport(templatePath = "/reports/nullsubreport.jrxml")
    static class NullSubreportReport extends ModularReport {

        ItemsModule itemsModule = null;

    }

    @JasperSubreport(templatePath = "/reports/currency_module.jrxml")
    static class CurrencyModule extends SubreportModule {

        String currencyCode;

        CurrencyModule(String currencyCode) {this.currencyCode = currencyCode;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperSubreport(templatePath = "/reports/financial_module.jrxml")
    static class FinancialModule extends SubreportModule {

        CurrencyModule currencyModule;

        FinancialModule(CurrencyModule currency) {this.currencyModule = currency;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperSubreport(templatePath = "/reports/summary_module.jrxml")
    static class SummaryModule extends SubreportModule {

        CurrencyModule currencyModule;

        SummaryModule(CurrencyModule currency) {this.currencyModule = currency;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperModularReport(templatePath = "/reports/company_report.jrxml")
    static class CompanyReport extends ModularReport {

        FinancialModule financialModule;
        SummaryModule summaryModule;

        CompanyReport(FinancialModule financial, SummaryModule summary) {
            this.financialModule = financial;
            this.summaryModule = summary;
        }
    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class ToggleModule extends SubreportModule {

        boolean empty;

        ToggleModule(boolean empty) {this.empty = empty;}

        @Override
        public boolean isEmpty() {return empty;}
    }

    @JasperModularReport(templatePath = "/reports/subreport.jrxml")
    static class ToggleReport extends ModularReport {

        ToggleModule toggleModule;

        ToggleReport(ToggleModule toggleModule) {this.toggleModule = toggleModule;}
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class ToggleListReport extends ModularReport {

        List<ToggleModule> modules;

        ToggleListReport(List<ToggleModule> modules) {this.modules = modules;}
    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class RichModule extends SubreportModule {

        String title;
        List<LineItem> items;

        RichModule(String title, List<LineItem> items) {
            this.title = title;
            this.items = items;
        }

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class RichListReport extends ModularReport {

        List<RichModule> modules;

        RichListReport(List<RichModule> modules) {this.modules = modules;}
    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    abstract static class MixedBaseModule extends SubreportModule {

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperSubreport(templatePath = "/reports/items.jrxml")
    static class MixedItemsModule extends MixedBaseModule {

        String title;

        MixedItemsModule(String title) {this.title = title;}
    }

    @JasperSubreport(templatePath = "/reports/other.jrxml")
    static class MixedOtherModule extends MixedBaseModule {

        String note;

        MixedOtherModule(String note) {this.note = note;}
    }

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class MixedListReport extends ModularReport {

        List<MixedBaseModule> modules;

        MixedListReport(List<MixedBaseModule> modules) {this.modules = modules;}
    }

}
