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

public class JasperModularDataFiller {

    protected Map<String, Object> parameters;

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

                    JasperSubreport ann = field.getType()
                                               .getAnnotation(JasperSubreport.class);

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
                    throw new JasperModularException("Failed to access field: " + field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return parameters;
    }

    private void validateCollectionElementType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType pt) {
            Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
            if (JasperModularDataFiller.class.isAssignableFrom(elementType)) {
                throw new JasperModularException(
                        "List of subreports is not supported. Field: " + field.getName()
                );
            }
        }
    }

    private void putSubreport(Field field,
                              JasperModularCompiler module,
                              JasperSubreport ann) {
        String prefix = ann.prefix().isEmpty() ? field.getType().getSimpleName()
                                               : ann.prefix();
        parameters.put(prefix + "Report", module.compileReport());
        parameters.put(prefix + "MapParameter", ((JasperModularDataFiller) module).fillMapParameters());
    }

    protected void putParameter(String key, Object value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    protected void putCollection(String key, Collection<?> data) {
        if (data != null && !data.isEmpty()) {
            parameters.put(key, new JRBeanCollectionDataSource(data));
        }
    }
}