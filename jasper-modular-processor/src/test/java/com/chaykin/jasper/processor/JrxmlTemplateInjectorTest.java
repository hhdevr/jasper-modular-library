package com.chaykin.jasper.processor;

import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.COLLECTION_PARAM_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.DATASET_FIELD_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.DATASET_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.EXISTING_DATASET_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.EXISTING_PARAM_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.EXISTING_SUBREPORT_PREFIX;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.SCALAR_PARAM_CLASS;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.SCALAR_PARAM_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.TABLE_PARAM_NAME;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.collectionParam;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.collectionParamWithCustomWidth;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.existingDatasetParam;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.existingScalarParam;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.existingSubreportParams;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.scalarParam;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.subreportParams;
import static com.chaykin.jasper.processor.JrxmlTemplateInjectorFixtures.tableParam;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JrxmlTemplateInjector")
class JrxmlTemplateInjectorTest {

    private static final String PREFILLED = "/reports/prefilled_test.jrxml";

    private CapturingMessager messager;

    private JrxmlTemplateInjector injector;

    static class CapturingMessager implements Messager {

        final List<String> notes = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
            collect(kind, msg);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {
            collect(kind, msg);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                                 Element e, AnnotationMirror a) {
            collect(kind, msg);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                                 Element e, AnnotationMirror a, AnnotationValue v) {
            collect(kind, msg);
        }

        private void collect(Diagnostic.Kind kind, CharSequence msg) {
            if (kind == Diagnostic.Kind.NOTE) {
                notes.add(msg.toString());
            }
            if (kind == Diagnostic.Kind.ERROR) {
                errors.add(msg.toString());
            }
        }

        boolean hasNote(String substring) {
            return notes.stream().anyMatch(n -> n.contains(substring));
        }

        long noteCount(String substring) {
            return notes.stream().filter(n -> n.contains(substring)).count();
        }

        void reset() {
            notes.clear();
            errors.clear();
        }
    }

    @BeforeEach
    void setUp() {
        messager = new CapturingMessager();
        injector = new JrxmlTemplateInjector(messager);
    }

    private JasperDesign blankTemplate() {
        return new JasperDesign();
    }

    private InputStream prefilledTemplate() {
        InputStream stream = getClass().getResourceAsStream(PREFILLED);
        assertThat(stream).as("prefilled_test.jrxml not found in src/test/resources/" + PREFILLED)
                          .isNotNull();
        return stream;
    }

    private JasperDesign inject(JasperDesign template,
                                List<com.chaykin.jasper.processor.model.JrxmlParameter> params)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        injector.inject(template, params, out);
        return JRXmlLoader.load(new ByteArrayInputStream(out.toByteArray()));
    }

    private JasperDesign inject(InputStream template,
                                List<com.chaykin.jasper.processor.model.JrxmlParameter> params)
            throws Exception {
        return inject(JRXmlLoader.load(template), params);
    }

    private byte[] injectToBytes(JasperDesign template,
                                 List<com.chaykin.jasper.processor.model.JrxmlParameter> params)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        injector.inject(template, params, out);
        return out.toByteArray();
    }

    private byte[] injectToBytes(InputStream template,
                                 List<com.chaykin.jasper.processor.model.JrxmlParameter> params)
            throws Exception {
        return injectToBytes(JRXmlLoader.load(template), params);
    }

    @Nested
    @DisplayName("parameters")
    class Parameters {

        @Test
        @DisplayName("new parameter is added to the design when it does not exist")
        void newParameter_isAddedToDesign() throws Exception {
            // given
            var params = scalarParam();

            // when
            JasperDesign design = inject(blankTemplate(), params);

            // then
            assertThat(design.getParametersMap()).containsKey(SCALAR_PARAM_NAME);
            assertThat(design.getParametersMap()
                             .get(SCALAR_PARAM_NAME)
                             .getValueClassName())
                    .isEqualTo(SCALAR_PARAM_CLASS);
        }

        @Test
        @DisplayName("existing parameter is not duplicated on re-injection")
        void existingParameter_isNotDuplicated() throws Exception {
            // given
            var params = existingScalarParam();

            // when
            JasperDesign design = inject(prefilledTemplate(), params);

            // then
            long count = design.getParametersList().stream()
                               .filter(p -> EXISTING_PARAM_NAME.equals(p.getName()))
                               .count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("skip note is logged when parameter already exists")
        void existingParameter_logsSkipNote() throws Exception {
            // given
            var params = existingScalarParam();

            // when
            inject(prefilledTemplate(), params);

            // then
            assertThat(messager.hasNote("skipping: " + EXISTING_PARAM_NAME)).isTrue();
        }
    }

    @Nested
    @DisplayName("datasets")
    class Datasets {

        @Test
        @DisplayName("new dataset with fields is added to the design")
        void newDataset_isAddedWithFields() throws Exception {
            // given
            var params = collectionParam();

            // when
            JasperDesign design = inject(blankTemplate(), params);

            // then
            assertThat(design.getDatasetMap()).containsKey(DATASET_NAME);
            JRField[] fields = design.getDatasetMap()
                                     .get(DATASET_NAME)
                                     .getFields();
            assertThat(Arrays.stream(fields))
                    .anyMatch(f -> DATASET_FIELD_NAME.equals(f.getName()));
        }

        @Test
        @DisplayName("existing dataset is not duplicated on re-injection")
        void existingDataset_isNotDuplicated() throws Exception {
            // given
            var params = existingDatasetParam();

            // when
            JasperDesign design = inject(prefilledTemplate(), params);

            // then
            long count = design.getDatasetsList().stream()
                               .filter(d -> EXISTING_DATASET_NAME.equals(d.getName()))
                               .count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("skip note is logged when dataset already exists")
        void existingDataset_logsSkipNote() throws Exception {
            // given
            var params = existingDatasetParam();

            // when
            inject(prefilledTemplate(), params);

            // then
            assertThat(messager.hasNote("skipping: " + EXISTING_DATASET_NAME)).isTrue();
        }
    }

    @Nested
    @DisplayName("list components")
    class ListComponents {

        @Test
        @DisplayName("list component is added to detail section for a collection parameter")
        void listComponent_isAddedToDetailSection() throws Exception {
            // given
            var params = collectionParam();

            // when
            inject(blankTemplate(), params);

            // then
            assertThat(messager.hasNote("Injected list component: " + COLLECTION_PARAM_NAME))
                    .isTrue();
        }

        @Test
        @DisplayName("list component is not duplicated on re-injection")
        void existingListComponent_isNotDuplicated() throws Exception {
            // given
            var params = collectionParam();
            byte[] firstResult = injectToBytes(blankTemplate(), params);

            messager.reset();

            // when
            inject(new ByteArrayInputStream(firstResult), params);

            // then
            assertThat(messager.hasNote("List component already exists - skipping: "
                                        + COLLECTION_PARAM_NAME)).isTrue();
            assertThat(messager.hasNote("Injected list component: "
                                        + COLLECTION_PARAM_NAME)).isFalse();
        }

        @Test
        @DisplayName("list component with custom column width is injected correctly")
        void listComponent_withCustomColumnWidth_isInjected() throws Exception {
            // given
            var params = collectionParamWithCustomWidth();

            // when
            inject(blankTemplate(), params);

            // then
            assertThat(messager.hasNote("Injected list component: " + COLLECTION_PARAM_NAME))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("table components")
    class TableComponents {

        @Test
        @DisplayName("table component is added to detail section for a table parameter")
        void tableComponent_isAddedToDetailSection() throws Exception {
            // given
            var params = tableParam();

            // when
            inject(blankTemplate(), params);

            // then
            assertThat(messager.hasNote("Injected table component: " + TABLE_PARAM_NAME))
                    .isTrue();
        }

        @Test
        @DisplayName("table component is not duplicated on re-injection")
        void existingTableComponent_isNotDuplicated() throws Exception {
            // given
            var params = tableParam();
            byte[] firstResult = injectToBytes(blankTemplate(), params);

            messager.reset();

            // when
            inject(new ByteArrayInputStream(firstResult), params);

            // then
            assertThat(messager.hasNote("List component already exists - skipping: "
                                        + TABLE_PARAM_NAME)).isTrue();
            assertThat(messager.hasNote("Injected table component: "
                                        + TABLE_PARAM_NAME)).isFalse();
        }

        @Test
        @DisplayName("table dataset is added alongside the table component")
        void tableComponent_addsDataset() throws Exception {
            // given
            var params = tableParam();

            // when
            JasperDesign design = inject(blankTemplate(), params);

            // then
            assertThat(design.getDatasetMap()).containsKey(TABLE_PARAM_NAME);
            JRField[] fields = design.getDatasetMap()
                                     .get(TABLE_PARAM_NAME)
                                     .getFields();
            assertThat(Arrays.stream(fields))
                    .anyMatch(f -> DATASET_FIELD_NAME.equals(f.getName()));
        }
    }

    @Nested
    @DisplayName("subreport bands")
    class SubreportBands {

        @Test
        @DisplayName("subreport band is added to detail section")
        void subreportBand_isAddedToDetailSection() throws Exception {
            // given
            var params = subreportParams();

            // when
            inject(blankTemplate(), params);

            // then
            assertThat(messager.hasNote("Injected subreport band: Items")).isTrue();
        }

        @Test
        @DisplayName("existing subreport band is not duplicated on re-injection")
        void existingSubreportBand_isNotDuplicated() throws Exception {
            // given
            var params = existingSubreportParams();

            // when
            inject(prefilledTemplate(), params);

            // then
            assertThat(messager.hasNote("Subreport band already exists - skipping: "
                                        + EXISTING_SUBREPORT_PREFIX)).isTrue();
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("injecting the same parameters twice produces identical parameter count")
        void injectTwice_doesNotGrowParameterCount() throws Exception {
            // given
            var params = scalarParam();
            byte[] firstResult = injectToBytes(blankTemplate(), params);
            int countAfterFirst = JRXmlLoader.load(new ByteArrayInputStream(firstResult))
                                             .getParametersList()
                                             .size();

            // when
            byte[] secondResult = injectToBytes(new ByteArrayInputStream(firstResult), params);
            int countAfterSecond = JRXmlLoader.load(new ByteArrayInputStream(secondResult))
                                              .getParametersList()
                                              .size();

            // then
            assertThat(countAfterSecond).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("injecting list and table params twice does not grow dataset count")
        void injectListAndTableTwice_doesNotGrowDatasetCount() throws Exception {
            // given
            var params = new ArrayList<com.chaykin.jasper.processor.model.JrxmlParameter>();
            params.addAll(collectionParam());
            params.addAll(tableParam());

            byte[] firstResult = injectToBytes(blankTemplate(), params);
            int countAfterFirst = JRXmlLoader.load(new ByteArrayInputStream(firstResult))
                                             .getDatasetsList()
                                             .size();

            // when
            byte[] secondResult = injectToBytes(
                    new ByteArrayInputStream(firstResult), params);
            int countAfterSecond = JRXmlLoader.load(new ByteArrayInputStream(secondResult))
                                              .getDatasetsList()
                                              .size();

            // then
            assertThat(countAfterSecond).isEqualTo(countAfterFirst);
        }
    }
}