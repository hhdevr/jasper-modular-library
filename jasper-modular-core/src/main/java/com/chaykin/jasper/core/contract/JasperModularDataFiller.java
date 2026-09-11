package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.model.SubreportModule;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class that builds the JasperReports parameters map from the fields of a report
 * or subreport module via reflection.
 */
public class JasperModularDataFiller {

    /** JRXML field name carrying each element's parameter map inside a generated subreport list. */
    public static final String SUBREPORT_PARAMS_FIELD = "params";

    /** JRXML field name carrying each element's own compiled report inside a generated subreport list. */
    public static final String SUBREPORT_REPORT_FIELD = "report";

    /** Suffix of the parameter carrying a subreport's compiled report: {@code <field>Report}. */
    public static final String REPORT_SUFFIX = "Report";

    /** Suffix of the parameter carrying a subreport's own parameters: {@code <field>MapParameter}. */
    public static final String MAP_PARAMETER_SUFFIX = "MapParameter";

    /** Suffix of the parameter carrying the rows of a subreport list: {@code <field>DataSource}. */
    public static final String DATA_SOURCE_SUFFIX = "DataSource";

    /** Suffix of the JRXML dataset that iterates a subreport list: {@code <field>Dataset}. */
    public static final String DATASET_SUFFIX = "Dataset";

    /**
     * Traverses all declared fields up the class hierarchy and builds the JasperReports
     * parameters map.
     *
     * @throws JasperModularException if a field breaks the wiring rules, a subreport template is
     *                                missing or does not compile, or subreports form a cycle
     */
    public Map<String, Object> fillMapParameters() {
        Map<String, Object> params = new HashMap<>();
        fillMapParameters(params, new HashSet<>());
        return params;
    }

    private void fillMapParameters(Map<String, Object> params, Set<Class<?>> visited) {
        if (!visited.add(this.getClass())) {
            throw new JasperModularException(
                    "Circular subreport dependency detected at: "
                    + this.getClass().getSimpleName()
                    + ". Visited chain: " + visited.stream()
                                                   .map(Class::getSimpleName)
                                                   .reduce((a, b) -> a + " -> " + b)
                                                   .orElse(""));
        }

        try {
            Class<?> clazz = this.getClass();
            while (clazz != null && clazz != JasperModularDataFiller.class) {
                for (Field field: clazz.getDeclaredFields()) {
                    processField(field, params, visited);
                }
                clazz = clazz.getSuperclass();
            }
        } finally {
            visited.remove(this.getClass());
        }
    }

    private void processField(Field field, Map<String, Object> params, Set<Class<?>> visited) {
        if (field.isSynthetic() || field.isAnnotationPresent(JasperIgnore.class)) {
            return;
        }

        field.setAccessible(true);
        try {
            Object value = field.get(this);
            if (value == null) {
                return;
            }

            Class<?> fieldType = field.getType();
            if (fieldType.isAnnotationPresent(JasperSubreport.class)) {
                requireModule(fieldType, field.getName());
                if (value instanceof SubreportModule module && !module.isEmpty()) {
                    putSubreport(field.getName(), module, params, visited);
                }
                return;
            }
            if (JasperModularDataFiller.class.isAssignableFrom(fieldType)) {
                throw new JasperModularException(
                        "Subreport fields must be declared with a @JasperSubreport-annotated type. "
                        + "Field: " + field.getName());
            }

            if (Collection.class.isAssignableFrom(fieldType)) {
                putCollectionField(field, (Collection<?>) value, params, visited);
            } else {
                putParameter(field.getName(), value, params);
            }

        } catch (IllegalAccessException e) {
            throw new JasperModularException(
                    "Failed to access field: " + field.getName(), e);
        }
    }

    private void putCollectionField(Field field, Collection<?> data,
                                    Map<String, Object> params,
                                    Set<Class<?>> visited) {
        Class<?> elementType = collectionElementType(field);
        if (elementType == null) {
            putCollection(field.getName(), data, params);
            return;
        }
        if (elementType.isRecord()) {
            throw new JasperModularException(
                    "Records are not supported as collection elements - JasperReports bean "
                    + "data sources require JavaBean getters. Field: " + field.getName());
        }
        if (elementType.isAnnotationPresent(JasperSubreport.class)) {
            requireModule(elementType, field.getName());
            putSubreportList(field.getName(), data, params, visited);
            return;
        }
        if (JasperModularDataFiller.class.isAssignableFrom(elementType)) {
            throw new JasperModularException(
                    "Collection elements that are modular reports must be annotated "
                    + "with @JasperSubreport. Field: " + field.getName());
        }
        putCollection(field.getName(), data, params);
    }

    private void requireFreeName(Map<String, Object> params, String key) {
        if (params.containsKey(key)) {
            throw new JasperModularException(
                    "Duplicate subreport parameter '" + key + "' in "
                    + this.getClass().getSimpleName()
                    + ". Two fields resolve to the same name, so one of them would be lost. "
                    + "Rename one of the fields.");
        }
    }

    private void requireModule(Class<?> type, String fieldName) {
        if (!SubreportModule.class.isAssignableFrom(type)) {
            throw new JasperModularException(
                    "@JasperSubreport class " + type.getSimpleName()
                    + " must extend SubreportModule. Field: " + fieldName);
        }
    }

    private Class<?> collectionElementType(Field field) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)
            || parameterizedType.getActualTypeArguments().length == 0) {
            return null;
        }
        Type argument = parameterizedType.getActualTypeArguments()[0];
        if (argument instanceof WildcardType wildcard) {
            argument = wildcard.getLowerBounds().length > 0
                       ? wildcard.getLowerBounds()[0]
                       : wildcard.getUpperBounds()[0];
        }
        return argument instanceof Class<?> elementType ? elementType : null;
    }

    private void putSubreport(String prefix,
                              SubreportModule module,
                              Map<String, Object> params,
                              Set<Class<?>> visited) {

        requireFreeName(params, prefix + REPORT_SUFFIX);

        Map<String, Object> childParams = childParameters(module, visited);
        params.put(prefix + REPORT_SUFFIX, module.compileReport());
        params.put(prefix + MAP_PARAMETER_SUFFIX, childParams);
    }

    /** Builds the data source of a repeating subreport from a collection of subreport modules. */
    private void putSubreportList(String prefix,
                                  Collection<?> data,
                                  Map<String, Object> params,
                                  Set<Class<?>> visited) {
        if (data.isEmpty()) {
            return;
        }

        List<Map<String, ?>> rows = new ArrayList<>();
        for (Object element: data) {
            SubreportModule module = (SubreportModule) element;
            if (module == null || module.isEmpty()) {
                continue;
            }
            rows.add(Map.of(SUBREPORT_PARAMS_FIELD,
                            childParameters(module, visited),
                            SUBREPORT_REPORT_FIELD,
                            module.compileReport()));
        }
        if (rows.isEmpty()) {
            return;
        }
        requireFreeName(params, prefix + DATA_SOURCE_SUFFIX);
        params.put(prefix + DATA_SOURCE_SUFFIX, new JRMapCollectionDataSource(rows));
    }

    private Map<String, Object> childParameters(JasperModularDataFiller module, Set<Class<?>> visited) {
        Map<String, Object> childParams = new HashMap<>();
        module.fillMapParameters(childParams, visited);
        return childParams;
    }

    /** Adds a scalar parameter, skipping {@code null} values. */
    protected void putParameter(String key, Object value, Map<String, Object> params) {
        if (value != null) {
            params.put(key, value);
        }
    }

    /** Wraps a non-empty collection in a {@link JRBeanCollectionDataSource} and stores it as a parameter. */
    protected void putCollection(String key, Collection<?> data, Map<String, Object> params) {
        if (data != null && !data.isEmpty()) {
            params.put(key, new JRBeanCollectionDataSource(data));
        }
    }

}
