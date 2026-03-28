package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.CollectionComponentType;
import com.chaykin.jasper.core.annotation.JasperCollection;
import com.chaykin.jasper.processor.model.JrxmlDataset;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;

import java.util.List;

/**
 * Test fixtures for {@link JrxmlTemplateInjectorTest}.
 * Provides pre-built {@link JrxmlParameter} lists that simulate
 * different combinations of report field types.
 */
final class JrxmlTemplateInjectorFixtures {

    private JrxmlTemplateInjectorFixtures() {}

    static final String SCALAR_PARAM_NAME = "customerName";
    static final String SCALAR_PARAM_CLASS = "java.lang.String";

    static final String COLLECTION_PARAM_NAME = "items";
    static final String COLLECTION_PARAM_CLASS =
            "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource";
    static final String DATASET_NAME = "items";
    static final String DATASET_FIELD_NAME = "price";
    static final String DATASET_FIELD_CLASS = "java.math.BigDecimal";
    static final int DEFAULT_COLUMN_WIDTH = JasperCollection.DEFAULT_COLUMN_WIDTH;
    static final int CUSTOM_COLUMN_WIDTH = 80;

    static final String TABLE_PARAM_NAME = "tableItems";
    static final String TABLE_DATASET_NAME = "tableItems";

    static final String SUBREPORT_REPORT_PARAM = "ItemsReport";
    static final String SUBREPORT_MAP_PARAM = "ItemsMapParameter";

    static final String EXISTING_PARAM_NAME = "existingParam";
    static final String EXISTING_DATASET_NAME = "existingDataset";
    static final String EXISTING_SUBREPORT_PREFIX = "Existing";

    static List<JrxmlParameter> scalarParam() {
        return List.of(
                new JrxmlParameter(SCALAR_PARAM_NAME, SCALAR_PARAM_CLASS, null)
        );
    }

    /**
     * Collection param with LIST component type and default column width.
     * Simulates a collection field without {@code @JasperCollection} annotation.
     */
    static List<JrxmlParameter> collectionParam() {
        JrxmlDatasetField field =
                new JrxmlDatasetField(DATASET_FIELD_NAME, DATASET_FIELD_CLASS);
        JrxmlDataset dataset = new JrxmlDataset(DATASET_NAME,
                                                List.of(field),
                                                CollectionComponentType.LIST,
                                                DEFAULT_COLUMN_WIDTH);
        return List.of(
                new JrxmlParameter(COLLECTION_PARAM_NAME, COLLECTION_PARAM_CLASS, dataset)
        );
    }

    /**
     * Collection param with LIST component type and custom column width.
     * Simulates {@code @JasperCollection(columnWidth = 80)}.
     */
    static List<JrxmlParameter> collectionParamWithCustomWidth() {
        JrxmlDatasetField field =
                new JrxmlDatasetField(DATASET_FIELD_NAME, DATASET_FIELD_CLASS);
        JrxmlDataset dataset = new JrxmlDataset(DATASET_NAME,
                                                List.of(field),
                                                CollectionComponentType.LIST,
                                                CUSTOM_COLUMN_WIDTH);
        return List.of(
                new JrxmlParameter(COLLECTION_PARAM_NAME, COLLECTION_PARAM_CLASS, dataset)
        );
    }

    /**
     * Collection param with TABLE component type and default column width.
     * Simulates {@code @JasperCollection(type = CollectionComponentType.TABLE)}.
     */
    static List<JrxmlParameter> tableParam() {
        JrxmlDatasetField field =
                new JrxmlDatasetField(DATASET_FIELD_NAME, DATASET_FIELD_CLASS);
        JrxmlDataset dataset = new JrxmlDataset(TABLE_DATASET_NAME,
                                                List.of(field),
                                                CollectionComponentType.TABLE,
                                                DEFAULT_COLUMN_WIDTH);
        return List.of(
                new JrxmlParameter(TABLE_PARAM_NAME, COLLECTION_PARAM_CLASS, dataset)
        );
    }

    static List<JrxmlParameter> subreportParams() {
        return List.of(
                new JrxmlParameter(SUBREPORT_REPORT_PARAM,
                                   "net.sf.jasperreports.engine.JasperReport", null),
                new JrxmlParameter(SUBREPORT_MAP_PARAM,
                                   "java.util.Map", null)
        );
    }

    static List<JrxmlParameter> existingScalarParam() {
        return List.of(
                new JrxmlParameter(EXISTING_PARAM_NAME, SCALAR_PARAM_CLASS, null)
        );
    }

    static List<JrxmlParameter> existingDatasetParam() {
        JrxmlDatasetField field =
                new JrxmlDatasetField("existingField", "java.lang.String");
        JrxmlDataset dataset = new JrxmlDataset(EXISTING_DATASET_NAME,
                                                List.of(field),
                                                CollectionComponentType.LIST,
                                                DEFAULT_COLUMN_WIDTH);
        return List.of(
                new JrxmlParameter(EXISTING_DATASET_NAME, COLLECTION_PARAM_CLASS, dataset)
        );
    }

    static List<JrxmlParameter> existingSubreportParams() {
        return List.of(
                new JrxmlParameter(EXISTING_SUBREPORT_PREFIX + "Report",
                                   "net.sf.jasperreports.engine.JasperReport", null),
                new JrxmlParameter(EXISTING_SUBREPORT_PREFIX + "MapParameter",
                                   "java.util.Map", null)
        );
    }
}