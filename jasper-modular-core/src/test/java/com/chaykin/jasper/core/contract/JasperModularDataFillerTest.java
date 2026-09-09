package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.BaseTypedSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ChildReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CollectionReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CompanyReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.CurrencyModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.FinancialModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.IgnoredFieldReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ItemsModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.LineItem;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MixedItemsModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MixedListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MixedOtherModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MixedReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.MultiSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NoPrefixReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NullSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.NullableReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.OtherModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.PojoListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.PojoSubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.RecordItem;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.RecordListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.RichListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.RichModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ScalarReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SelfNodeModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SpecialItemsModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.StaticFieldReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SubreportListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SubreportReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.SummaryModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ToggleListReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ToggleModule;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.ToggleReport;
import com.chaykin.jasper.core.contract.JasperModularDataFillerFixture.WildcardListReport;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
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

        @Test
        @DisplayName("static fields are placed into the map unless annotated with @JasperIgnore")
        void staticField_isAddedToMapUnlessIgnored() {
            // given
            var report = new StaticFieldReport();

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsEntry("STATIC_CONSTANT", "shared")
                              .containsKey("instanceField")
                              .doesNotContainKey("IGNORED_CONSTANT");
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
        @DisplayName("List of records throws - bean data sources cannot read record accessors")
        void listOfRecords_throwsException() {
            // given
            var report = new RecordListReport(List.of(new RecordItem("x")));

            // when / then
            assertThatThrownBy(report::fillMapParameters)
                    .isInstanceOf(JasperModularException.class)
                    .hasMessageContaining("Records are not supported as collection elements");
        }

    }

    @Nested
    @DisplayName("subreport fields")
    class SubreportFields {

        @Test
        @DisplayName("subreport field produces <field>Report and <field>MapParameter")
        void subreportField_producesTwoEntriesNamedAfterTheField() {
            // given
            var report = new SubreportReport(new ItemsModule("Section A"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("itemsModuleReport")
                    .containsKey("itemsModuleMapParameter");
        }

        @Test
        @DisplayName("subclass instance of an annotated module is classified by the declared field type")
        void subclassInstance_usesDeclaredTypeWiring() {
            // given
            var report = new SubreportReport(new SpecialItemsModule("A"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("itemsModuleReport")
                              .containsKey("itemsModuleMapParameter")
                              .doesNotContainKey("section");
        }

        @Test
        @DisplayName("field declared with an unannotated module type throws")
        void unannotatedDeclaredModuleType_throwsException() {
            // given
            var report = new BaseTypedSubreportReport(new ItemsModule("A"));

            // when / then
            assertThatThrownBy(report::fillMapParameters)
                    .isInstanceOf(JasperModularException.class)
                    .hasMessageContaining("@JasperSubreport-annotated type");
        }

        @Test
        @DisplayName("annotated class that does not extend SubreportModule throws a clear error")
        void annotatedPojo_throwsException() {
            // given
            var report = new PojoSubreportReport();

            // when / then
            assertThatThrownBy(report::fillMapParameters)
                    .isInstanceOf(JasperModularException.class)
                    .hasMessageContaining("PojoModule must extend SubreportModule");
        }

        @Test
        @DisplayName("list of annotated classes that do not extend SubreportModule throws a clear error")
        void annotatedPojoList_throwsException() {
            // given
            var report = new PojoListReport();

            // when / then
            assertThatThrownBy(report::fillMapParameters)
                    .isInstanceOf(JasperModularException.class)
                    .hasMessageContaining("PojoModule must extend SubreportModule");
        }

        @Test
        @DisplayName("parameter name comes from the field, not from the module type")
        void subreportField_isNamedAfterFieldNotType() {
            // given
            var report = new NoPrefixReport(new OtherModule("note"));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params)
                    .containsKey("otherModuleReport")
                    .containsKey("otherModuleMapParameter");
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
                    .containsKey("itemsModuleReport")
                    .containsKey("itemsModuleMapParameter")
                    .containsKey("otherModuleReport")
                    .containsKey("otherModuleMapParameter");
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
                    .containsKey("itemsModuleReport")
                    .containsKey("itemsModuleMapParameter");
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
                .doesNotContainKey("itemsModuleReport")
                .doesNotContainKey("itemsModuleMapParameter");
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
                    .containsKey("financialModuleReport")
                    .containsKey("financialModuleMapParameter")
                    .containsKey("summaryModuleReport")
                    .containsKey("summaryModuleMapParameter");
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
                    (Map<String, Object>) params.get("financialModuleMapParameter");
            assertThat(financialParams).containsKey("currencyModuleReport");

            @SuppressWarnings("unchecked")
            Map<String, Object> summaryParams =
                    (Map<String, Object>) params.get("summaryModuleMapParameter");
            assertThat(summaryParams).containsKey("currencyModuleReport");
        }

    }

    @Nested
    @DisplayName("subreport lists")
    class SubreportLists {

        @Test
        @DisplayName("List of subreport modules produces a single <prefix>DataSource of element maps")
        void listOfSubreportModules_producesRepeatingSubreport() {
            // given
            var report = new SubreportListReport(List.of(new ItemsModule("a"), new ItemsModule("b")));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then - the compiled report rides in the data source rows, not as a separate param
            assertThat(params).containsKey("modulesDataSource")
                              .doesNotContainKey("itemsModuleReport");
            assertThat(params.get("modulesDataSource")).isInstanceOf(JRMapCollectionDataSource.class);
        }

        @Test
        @DisplayName("data source carries one row per element")
        void dataSource_hasOneRowPerElement() {
            // given
            var report = new SubreportListReport(
                    List.of(new ItemsModule("a"), new ItemsModule("b"), new ItemsModule("c")));

            // when
            var dataSource =
                    (JRMapCollectionDataSource) report.fillMapParameters().get("modulesDataSource");

            // then
            int rows = 0;
            while (dataSource.next()) {
                rows++;
            }
            assertThat(rows).isEqualTo(3);
        }

        @Test
        @DisplayName("empty list produces no parameters")
        void emptyList_isSkipped() {
            // given
            var report = new SubreportListReport(List.of());

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).doesNotContainKey("itemsModuleReport")
                              .doesNotContainKey("modulesDataSource");
        }

        @Test
        @DisplayName("mixed module subclasses each render with their own compiled template")
        void mixedSubclasses_eachRowCarriesOwnTemplate() {
            // given
            var report = new MixedListReport(
                    List.of(new MixedItemsModule("a"), new MixedOtherModule("b")));

            // when
            var dataSource =
                    (JRMapCollectionDataSource) report.fillMapParameters().get("modulesDataSource");

            // then - each row carries its element's template, not the first element's
            JRDesignField reportField = new JRDesignField();
            reportField.setName("report");

            assertThat(dataSource.next()).isTrue();
            Object firstRowReport = dataSource.getFieldValue(reportField);
            assertThat(dataSource.next()).isTrue();
            Object secondRowReport = dataSource.getFieldValue(reportField);

            assertThat(firstRowReport).isSameAs(new MixedItemsModule("x").compileReport());
            assertThat(secondRowReport).isSameAs(new MixedOtherModule("y").compileReport());
            assertThat(firstRowReport).isNotSameAs(secondRowReport);
        }

        @Test
        @DisplayName("self-referential subreport module throws circular dependency")
        void selfReferentialModule_throwsCircularDependency() {
            // given
            var root = new SelfNodeModule(List.of(new SelfNodeModule(List.of())));

            // when / then
            assertThatThrownBy(root::fillMapParameters)
                    .isInstanceOf(JasperModularException.class)
                    .hasMessageContaining("Circular subreport dependency");
        }
    }

    @Nested
    @DisplayName("empty subreports (isEmpty)")
    class EmptySubreports {

        @Test
        @DisplayName("empty single subreport is skipped - no parameters produced")
        void emptySingleSubreport_isSkipped() {
            // given
            var report = new ToggleReport(new ToggleModule(true));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).doesNotContainKey("toggleModuleReport")
                              .doesNotContainKey("toggleModuleMapParameter");
        }

        @Test
        @DisplayName("non-empty single subreport produces its two parameters")
        void nonEmptySingleSubreport_isIncluded() {
            // given
            var report = new ToggleReport(new ToggleModule(false));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("toggleModuleReport")
                              .containsKey("toggleModuleMapParameter");
        }

        @Test
        @DisplayName("empty elements are dropped from a subreport list")
        void emptyElements_areDroppedFromList() {
            // given
            var report = new ToggleListReport(List.of(
                    new ToggleModule(false), new ToggleModule(true), new ToggleModule(false)));

            // when
            var dataSource =
                    (JRMapCollectionDataSource) report.fillMapParameters().get("modulesDataSource");

            // then
            int rows = 0;
            while (dataSource.next()) {
                rows++;
            }
            assertThat(rows).isEqualTo(2);
        }

        @Test
        @DisplayName("a list of only empty elements produces no data source parameter")
        void allEmptyList_producesNoParameter() {
            // given
            var report = new ToggleListReport(
                    List.of(new ToggleModule(true), new ToggleModule(true)));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).doesNotContainKey("modulesDataSource");
        }

        @Test
        @DisplayName("null elements are skipped without producing a row")
        void nullElements_areSkipped() throws Exception {
            // given
            var report = new SubreportListReport(
                    Arrays.asList(new ItemsModule("A"), null, new ItemsModule("B")));

            // when
            var dataSource =
                    (JRMapCollectionDataSource) report.fillMapParameters().get("modulesDataSource");

            // then
            int rows = 0;
            while (dataSource.next()) {
                rows++;
            }
            assertThat(rows).isEqualTo(2);
        }

        @Test
        @DisplayName("wildcard element type is resolved to its bound")
        void wildcardElementType_producesRepeatingSubreport() {
            // given
            var report = new WildcardListReport(List.of(new ItemsModule("A")));

            // when
            Map<String, Object> params = report.fillMapParameters();

            // then
            assertThat(params).containsKey("modulesDataSource")
                              .doesNotContainKey("modules");
        }

        @Test
        @DisplayName("row map of a list element carries its own collection as a data source")
        void elementWithCollection_rowMapCarriesDataSource() {
            // given
            var report = new RichListReport(List.of(
                    new RichModule("Section A", List.of(new LineItem("Widget", BigDecimal.TEN)))));

            // when
            var dataSource =
                    (JRMapCollectionDataSource) report.fillMapParameters().get("modulesDataSource");

            // then
            assertThat(dataSource.next()).isTrue();

            JRDesignField paramsField = new JRDesignField();
            paramsField.setName("params");
            @SuppressWarnings("unchecked")
            Map<String, Object> rowParams =
                    (Map<String, Object>) dataSource.getFieldValue(paramsField);
            assertThat(rowParams).containsEntry("title", "Section A");
            assertThat(rowParams.get("items")).isInstanceOf(JRBeanCollectionDataSource.class);

            JRDesignField reportField = new JRDesignField();
            reportField.setName("report");
            assertThat(dataSource.getFieldValue(reportField)).isInstanceOf(JasperReport.class);
        }
    }

}
