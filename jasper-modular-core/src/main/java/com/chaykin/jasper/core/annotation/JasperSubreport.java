package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperSubreport {

    String templatePath();

    String prefix() default "";

    GenerationMode mode() default GenerationMode.INJECT;
}
