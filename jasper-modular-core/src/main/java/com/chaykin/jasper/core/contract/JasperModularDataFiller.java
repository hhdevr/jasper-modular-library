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

    /**
     * Traverses all declared fields up the class hierarchy and builds the JasperReports
     * parameters map.
     *
     * @throws JasperModularException on reflection failure, subreport compilation failure, or a
     *                                circular subreport dependency
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
            JasperSubreport annotation = fieldType.getAnnotation(JasperSubreport.class);
            if (annotation != null) {
                requireModule(fieldType, field.getName());
                if (value instanceof SubreportModule module && module.isEmpty()) {
                    return;
                }
                putSubreport(fieldType, (JasperModularCompiler) value, annotation, params, visited);
                return;
            }
            if (JasperModularDataFiller.class.isAssignableFrom(fieldType)) {
                throw new JasperModularException(
                        "Subreport fields must be declared with a @JasperSubreport-annotated type. "
                        + "Field: " + field.getName());
            }

            if (Collection.class.isAssignableFrom(field.getType())) {
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
            putSubreportList(data, elementType, params, visited);
            return;
        }
        if (JasperModularDataFiller.class.isAssignableFrom(elementType)) {
            throw new JasperModularException(
                    "Collection elements that are modular reports must be annotated "
                    + "with @JasperSubreport. Field: " + field.getName());
        }
        putCollection(field.getName(), data, params);
    }

    private void requireModule(Class<?> type, String fieldName) {
        if (!JasperModularDataFiller.class.isAssignableFrom(type)) {
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

    private void putSubreport(Class<?> declaredType,
                              JasperModularCompiler module,
                              JasperSubreport annotation,
                              Map<String, Object> params,
                              Set<Class<?>> visited) {

        String prefix = annotation.prefix().isEmpty()
                        ? declaredType.getSimpleName()
                        : annotation.prefix();

        Map<String, Object> childParams = new HashMap<>();
        ((JasperModularDataFiller) module).fillMapParameters(childParams, visited);
        params.put(prefix + "Report", module.compileReport());
        params.put(prefix + "MapParameter", childParams);
    }

    /** Renders a collection of {@link JasperSubreport}-annotated modules as a repeating subreport. */
    private void putSubreportList(Collection<?> data,
                                  Class<?> elementType,
                                  Map<String, Object> params,
                                  Set<Class<?>> visited) {
        if (data.isEmpty()) {
            return;
        }

        JasperSubreport annotation = elementType.getAnnotation(JasperSubreport.class);
        String prefix = annotation.prefix().isEmpty() ? elementType.getSimpleName() : annotation.prefix();

        List<Map<String, ?>> rows = new ArrayList<>();
        for (Object element: data) {
            if (element == null || (element instanceof SubreportModule module && module.isEmpty())) {
                continue;
            }
            Map<String, Object> childParams = new HashMap<>();
            ((JasperModularDataFiller) element).fillMapParameters(childParams, visited);
            rows.add(Map.of(SUBREPORT_PARAMS_FIELD,
                            childParams,
                            SUBREPORT_REPORT_FIELD,
                            ((JasperModularCompiler) element).compileReport()));
        }
        if (rows.isEmpty()) {
            return;
        }
        params.put(prefix + "DataSource", new JRMapCollectionDataSource(rows));
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
