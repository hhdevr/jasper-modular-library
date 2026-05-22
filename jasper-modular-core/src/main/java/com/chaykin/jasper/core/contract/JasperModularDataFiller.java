package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class responsible for building the JasperReports parameters map from the fields
 * of a report or subreport module at runtime.
 *
 * <p>All report and subreport classes inherit from this class via
 * {@link com.chaykin.jasper.core.model.ModularReport} or
 * {@link com.chaykin.jasper.core.model.SubreportModule}. Calling {@link #fillMapParameters()}
 * triggers reflection-based traversal of all declared fields up the class hierarchy
 * (stopping at this class), building a {@code Map<String, Object>} that is passed directly
 * to {@code JasperFillManager.fillReport}.</p>
 *
 * <h2>Field handling rules</h2>
 * <ul>
 *   <li>Fields annotated with {@link JasperIgnore} are skipped entirely.</li>
 *   <li>Fields whose runtime type is annotated with {@link JasperSubreport} are treated as
 *       embedded subreports. Two entries are added: {@code <prefix>Report} (the compiled
 *       {@code JasperReport}) and {@code <prefix>MapParameter}
 *       (the subreport's own parameters map, filled recursively).</li>
 *   <li>Fields of type {@link Collection} are wrapped in a {@link JRBeanCollectionDataSource}
 *       and stored as a parameter under the field name — referenced from the JRXML via
 *       {@code $P{fieldName}}, not as the root data source. Collections of subreport modules
 *       are not supported and will throw at runtime.</li>
 *   <li>All other non-null fields are stored as plain parameters under the field name.</li>
 * </ul>
 *
 * <h2>Circular dependency detection</h2>
 * <p>The recursive subreport filling is protected against circular dependencies.
 * If a cycle is detected (e.g. {@code A -> B -> A}), a {@link JasperModularException}
 * is thrown immediately with a clear message identifying the offending class,
 * rather than allowing a {@code StackOverflowError} to propagate.</p>
 *
 * <p>Diamond-shaped graphs (e.g. {@code A -> B}, {@code A -> C}, {@code B -> D},
 * {@code C -> D}) are handled correctly: the shared module {@code D} is visited
 * independently in both branches without false cycle detection.</p>
 */
public class JasperModularDataFiller {

    /**
     * Traverses all declared fields up the class hierarchy and builds the JasperReports
     * parameters map.
     *
     * @return a non-null map of parameter names to values, ready for {@code JasperFillManager}
     * @throws JasperModularException on reflection failure, subreport compilation failure,
     *                                a circular subreport dependency, or a {@code List} of
     *                                subreport modules
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

            JasperSubreport ann = value.getClass().getAnnotation(JasperSubreport.class);
            if (ann != null) {
                putSubreport(field, (JasperModularCompiler) value, ann, params, visited);
                return;
            }

            if (Collection.class.isAssignableFrom(field.getType())) {
                validateCollectionElementType(field);
                putCollection(field.getName(), (Collection<?>) value, params);
            } else {
                putParameter(field.getName(), value, params);
            }

        } catch (IllegalAccessException e) {
            throw new JasperModularException(
                    "Failed to access field: " + field.getName(), e);
        }
    }

    private void validateCollectionElementType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType pt) {
            Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
            if (JasperModularDataFiller.class.isAssignableFrom(elementType)) {
                throw new JasperModularException(
                        "List of subreports is not supported. Field: " + field.getName());
            }
        }
    }

    private void putSubreport(Field field, JasperModularCompiler module,
                              JasperSubreport ann, Map<String, Object> params,
                              Set<Class<?>> visited) {
        String prefix = ann.prefix().isEmpty()
                        ? module.getClass().getSimpleName()
                        : ann.prefix();
        Map<String, Object> childParams = new HashMap<>();
        ((JasperModularDataFiller) module).fillMapParameters(childParams, visited);
        params.put(prefix + "Report", module.compileReport());
        params.put(prefix + "MapParameter", childParams);
    }

    /**
     * Adds a scalar parameter, skipping {@code null} values. Protected so subclasses
     * overriding {@link #fillMapParameters()} can inject custom parameters.
     */
    protected void putParameter(String key, Object value, Map<String, Object> params) {
        if (value != null) {
            params.put(key, value);
        }
    }

    /**
     * Wraps a non-empty collection in a {@link JRBeanCollectionDataSource} and stores it as
     * a parameter. Empty and {@code null} collections are ignored.
     */
    protected void putCollection(String key, Collection<?> data, Map<String, Object> params) {
        if (data != null && !data.isEmpty()) {
            params.put(key, new JRBeanCollectionDataSource(data));
        }
    }

}
