package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ChildReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CollectionReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CompanyReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CurrencyModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.FinancialModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.IgnoredFieldReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ItemsModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.LineItem;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MixedReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MultiSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NoPrefixReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NullSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NullableReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.OtherModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ScalarReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SubreportListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SummaryModule;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JasperModularDataFiller")
class JasperModularDataFillerTest {

    @Nested
    @DisplayName("scalar fields")
    class ScalarFields {

        @Test
        @DisplayName("non-null scalar fields are placed into the map under their field names")
        void nonNullScalars_arePlacedIntoMap() {
            // given
            var report = new ScalarReport("Acme", BigDecimal.valueOf(1500));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsEntry("customerName", "Acme")
                              .containsEntry("total", BigDecimal.valueOf(1500));
        }

        @Test
        @DisplayName("null scalar field is silently skipped and does not appear in the map")
        void nullScalar_isNotAddedToMap() {
            // given
            var report = new NullableReport();

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("present")
                              .doesNotContainKey("absent");
        }
    }

    @Nested
    @DisplayName("@JasperIgnore")
    class JasperIgnoreFields {

        @Test
        @DisplayName("field annotated with @JasperIgnore is excluded from the map")
        void ignoredField_isExcludedFromMap() {
            // given
            var report = new IgnoredFieldReport();

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("visible")
                              .doesNotContainKey("hidden");
        }
    }

    @Nested
    @DisplayName("collection fields")
    class CollectionFields {

        @Test
        @DisplayName("non-empty List is wrapped in JRBeanCollectionDataSource")
        void nonEmptyList_isWrappedInDataSource() {
            // given
            var items = List.of(new LineItem("Widget", BigDecimal.TEN));
            var report = new CollectionReport(items);

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("items");
            assertThat(params.get("items")).isInstanceOf(JRBeanCollectionDataSource.class);
        }

        @Test
        @DisplayName("empty List is not added to the map")
        void emptyList_isNotAddedToMap() {
            // given
            var report = new CollectionReport(List.of());

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).doesNotContainKey("items");
        }

        @Test
        @DisplayName("null List is not added to the map")
        void nullList_isNotAddedToMap() {
            // given
            var report = new CollectionReport(null);

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).doesNotContainKey("items");
        }

        @Test
        @DisplayName("List whose element type is a subreport module throws JasperModularException")
        void listOfSubreportModules_throwsException() {
            // given
            var report = new SubreportListReport(List.of(new ItemsModule("x")));

            // when / then
            assertThatThrownBy(report::fillMapParameters).isInstanceOf(JasperModularException.class)
                                                         .hasMessageContaining("List of subreports is not supported. Field: modules");
        }
    }

    @Nested
    @DisplayName("subreport fields")
    class SubreportFields {

        @Test
        @DisplayName("subreport field produces <prefix>Report and <prefix>MapParameter using annotation prefix")
        void subreportField_producesTwoEntriesWithAnnotationPrefix() {
            // given
            var report = new SubreportReport(new ItemsModule("Section A"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("ItemsReport")
                    .containsKey("ItemsMapParameter");
        }

        @Test
        @DisplayName("subreport field uses simple class name as prefix when prefix attribute is not set")
        void subreportField_usesClassNameAsPrefixWhenNotSet() {
            // given
            var report = new NoPrefixReport(new OtherModule("note"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("OtherModuleReport")
                    .containsKey("OtherModuleMapParameter");
        }

        @Test
        @DisplayName("multiple subreport fields each produce their own pair of parameters")
        void multipleSubreportFields_eachProduceOwnParameters() {
            // given
            var report = new MultiSubreportReport(
                    new ItemsModule("A"),
                    new OtherModule("B")
            );

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("ItemsReport")
                    .containsKey("ItemsMapParameter")
                    .containsKey("OtherModuleReport")
                    .containsKey("OtherModuleMapParameter");
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        @Test
        @DisplayName("fields declared in a superclass are included in the map")
        void superclassFields_areIncludedInMap() {
            // given
            var report = new ChildReport();

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("childField")
                    .containsKey("parentField");
        }
    }

    @Nested
    @DisplayName("mixed report")
    class MixedReports {

        @Test
        @DisplayName("report with scalars, collection and subreport populates all field types correctly")
        void mixedReport_allFieldTypesArePopulated() {
            // given
            var items = List.of(new LineItem("Gear", BigDecimal.ONE));
            var report = new MixedReport("Invoice", items, new ItemsModule("Details"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsEntry("title", "Invoice");
            assertThat(params.get("items")).isInstanceOf(JRBeanCollectionDataSource.class);
            assertThat(params)
                    .containsKey("ItemsReport")
                    .containsKey("ItemsMapParameter");
        }
    }

    @Test
    @DisplayName("null subreport field is silently skipped and does not produce parameters")
    void nullSubreportField_isNotAddedToMap() {
        // given
        var report = new NullSubreportReport();

        // when
        Map<String, Object> params = report.fillMapParameters();

        // then
        assertThat(params)
                .doesNotContainKey("ItemsReport")
                .doesNotContainKey("ItemsMapParameter");
    }

    @Nested
    @DisplayName("shared subreport reachable via two branches")
    class SharedSubreportHandling {

        @Test
        @DisplayName("shared subreport reachable via two branches does not throw")
        void sharedSubreport_reachableViaTwoBranches_doesNotThrow() {
            // given
            var currency = new CurrencyModule("USD");
            var report = new CompanyReport(
                    new FinancialModule(currency),
                    new SummaryModule(currency)
            );

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("FinancialReport")
                    .containsKey("FinancialMapParameter")
                    .containsKey("SummaryReport")
                    .containsKey("SummaryMapParameter");
        }

        @Test
        @DisplayName("each branch's MapParameter independently contains the shared subreport's params")
        void sharedSubreport_eachBranchHasItsOwnParams() {
            // given
            var currency = new CurrencyModule("EUR");
            var report = new CompanyReport(
                    new FinancialModule(currency),
                    new SummaryModule(currency)
            );

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            @SuppressWarnings("unchecked")
            Map<String, Object> financialParams =
                    (Map<String, Object>) params.get("FinancialMapParameter");
            assertThat(financialParams).containsKey("CurrencyReport");

            @SuppressWarnings("unchecked")
            Map<String, Object> summaryParams =
                    (Map<String, Object>) params.get("SummaryMapParameter");
            assertThat(summaryParams).containsKey("CurrencyReport");
        }

    }

}
