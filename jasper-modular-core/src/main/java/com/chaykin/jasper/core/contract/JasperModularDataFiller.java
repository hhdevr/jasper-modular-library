package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.model.SubreportModule;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
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
        if (field.isAnnotationPresent(JasperIgnore.class)) {
            return;
        }

        field.setAccessible(true);
        try {
            Object value = field.get(this);
            if (value == null) {
                return;
            }

            JasperSubreport annotation = value.getClass().getAnnotation(JasperSubreport.class);
            if (annotation != null) {
                if (value instanceof SubreportModule module && module.isEmpty()) {
                    return;
                }
                putSubreport((JasperModularCompiler) value, annotation, params, visited);
                return;
            }

            if (Collection.class.isAssignableFrom(field.getType())) {
                Class<?> elementType = collectionElementType(field);
                if (elementType != null && elementType.isAnnotationPresent(JasperSubreport.class)) {
                    putSubreportList((Collection<?>) value, elementType, params, visited);
                } else if (elementType != null
                           && JasperModularDataFiller.class.isAssignableFrom(elementType)) {
                    throw new JasperModularException(
                            "Collection elements that are modular reports must be annotated "
                            + "with @JasperSubreport. Field: " + field.getName());
                } else {
                    putCollection(field.getName(), (Collection<?>) value, params);
                }
            } else {
                putParameter(field.getName(), value, params);
            }

        } catch (IllegalAccessException e) {
            throw new JasperModularException(
                    "Failed to access field: " + field.getName(), e);
        }
    }

    private Class<?> collectionElementType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType parameterizedType
            && parameterizedType.getActualTypeArguments().length > 0
            && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            return elementType;
        }
        return null;
    }

    private void putSubreport(JasperModularCompiler module,
                              JasperSubreport annotation,
                              Map<String, Object> params,
                              Set<Class<?>> visited) {

        String prefix = annotation.prefix().isEmpty()
                        ? module.getClass().getSimpleName()
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
            if (element instanceof SubreportModule module && module.isEmpty()) {
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
