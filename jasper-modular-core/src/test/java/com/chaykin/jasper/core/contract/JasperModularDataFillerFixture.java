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

    @JasperSubreport(templatePath = "/reports/items.jrxml", prefix = "Items")
    static class ItemsModule extends SubreportModule {

        String title;

        ItemsModule(String title) {this.title = title;}

        @Override
        public boolean isEmpty() {return false;}

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

    @JasperModularReport(templatePath = "/reports/subreportlist.jrxml")
    static class SubreportListReport extends ModularReport {

        List<ItemsModule> modules;

        SubreportListReport(List<ItemsModule> modules) {this.modules = modules;}

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

    @JasperSubreport(templatePath = "/reports/currency_module.jrxml", prefix = "Currency")
    static class CurrencyModule extends SubreportModule {

        String currencyCode;

        CurrencyModule(String currencyCode) {this.currencyCode = currencyCode;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperSubreport(templatePath = "/reports/financial_module.jrxml", prefix = "Financial")
    static class FinancialModule extends SubreportModule {

        CurrencyModule currencyModule;

        FinancialModule(CurrencyModule currency) {this.currencyModule = currency;}

        @Override
        public boolean isEmpty() {return false;}
    }

    @JasperSubreport(templatePath = "/reports/summary_module.jrxml", prefix = "Summary")
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

}
