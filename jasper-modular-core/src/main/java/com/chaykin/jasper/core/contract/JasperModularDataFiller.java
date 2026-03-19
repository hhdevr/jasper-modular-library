package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
 *   <li>Fields whose type is annotated with {@link JasperSubreport} are treated as embedded
 *       subreports. Two entries are added: {@code <prefix>Report} (the compiled
 *       {@code JasperReport}) and {@code <prefix>MapParameter}
 *       (the subreport's own parameters map, filled recursively).</li>
 *   <li>Fields of type {@link Collection} are wrapped in a
 *       {@link JRBeanCollectionDataSource} and stored under the field name.
 *       Collections of subreport types are not supported and will throw at runtime.</li>
 *   <li>All other non-null fields are stored as plain parameters under the field name.</li>
 * </ul>
 */
public class JasperModularDataFiller {

    /**
     * The parameters map being built during {@link #fillMapParameters()}.
     * Available to subclasses for custom parameter injection via {@link #putParameter}.
     */
    protected Map<String, Object> parameters;

    /**
     * Creates a new {@code JasperModularDataFiller} instance.
     * Subclasses do not need to call this constructor explicitly.
     */
    public JasperModularDataFiller() {
    }

    /**
     * Traverses all declared fields of this instance's class hierarchy and builds
     * the JasperReports parameters map.
     *
     * <p>Traversal walks up the class hierarchy from the concrete class toward
     * (but not including) {@link JasperModularDataFiller} itself. Fields are processed
     * according to the rules described in the class-level documentation.</p>
     *
     * @return a non-null map of parameter names to their values, ready for use with
     * {@code JasperFillManager}
     * @throws JasperModularException if a field cannot be accessed via reflection,
     *                                if a subreport compilation fails,
     *                                or if a {@code List} of subreport modules is detected
     */
    public Map<String, Object> fillMapParameters() {
        parameters = new HashMap<>();

        Class<?> clazz = this.getClass();

        while (clazz != null && clazz != JasperModularDataFiller.class) {
            for (Field field: clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(JasperIgnore.class)) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object value = field.get(this);
                    if (value == null) {
                        continue;
                    }

                    JasperSubreport ann = field.getType().getAnnotation(JasperSubreport.class);

                    if (ann != null) {
                        putSubreport(field, (JasperModularCompiler) value, ann);
                        continue;
                    }

                    if (Collection.class.isAssignableFrom(field.getType())) {
                        validateCollectionElementType(field);
                        putCollection(field.getName(), (Collection<?>) value);
                    } else {
                        putParameter(field.getName(), value);
                    }

                } catch (IllegalAccessException e) {
                    throw new JasperModularException(
                            "Failed to access field: " + field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return parameters;
    }

    /**
     * Validates that a {@link Collection} field does not contain subreport elements.
     *
     * <p>Collections of classes that extend {@link JasperModularDataFiller} are not
     * supported because subreports must be handled individually to allow each to compile
     * and fill its own template independently.</p>
     *
     * @param field the collection field to validate
     * @throws JasperModularException if the collection's element type is a subreport module
     */
    private void validateCollectionElementType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType pt) {
            Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
            if (JasperModularDataFiller.class.isAssignableFrom(elementType)) {
                throw new JasperModularException(
                        "List of subreports is not supported. Field: " + field.getName());
            }
        }
    }

    /**
     * Puts a subreport's compiled template and parameters map into the parameters map.
     *
     * <p>Two entries are added:</p>
     * <ul>
     *   <li>{@code <prefix>Report} - the compiled {@code JasperReport}</li>
     *   <li>{@code <prefix>MapParameter} - the subreport's filled parameters map</li>
     * </ul>
     *
     * @param field  the field holding the subreport module instance
     * @param module the subreport module
     * @param ann    the {@link JasperSubreport} annotation on the subreport's class
     */
    private void putSubreport(Field field, JasperModularCompiler module, JasperSubreport ann) {
        String prefix = ann.prefix().isEmpty()
                        ? field.getType().getSimpleName()
                        : ann.prefix();
        parameters.put(prefix + "Report", module.compileReport());
        parameters.put(prefix + "MapParameter",
                       ((JasperModularDataFiller) module).fillMapParameters());
    }

    /**
     * Adds a scalar parameter to the parameters map, skipping {@code null} values.
     *
     * @param key   the parameter name as it appears in the JRXML
     * @param value the parameter value; {@code null} values are silently ignored
     */
    protected void putParameter(String key, Object value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    /**
     * Wraps a non-empty collection in a {@link JRBeanCollectionDataSource} and adds it
     * to the parameters map under the given key.
     *
     * <p>Empty and {@code null} collections are silently ignored.</p>
     *
     * @param key  the parameter name as it appears in the JRXML
     * @param data the collection to wrap; ignored if {@code null} or empty
     */
    protected void putCollection(String key, Collection<?> data) {
        if (data != null && !data.isEmpty()) {
            parameters.put(key, new JRBeanCollectionDataSource(data));
        }
    }
}
