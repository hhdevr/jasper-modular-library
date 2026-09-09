package com.chaykin.jasper.processor.model;

import com.chaykin.jasper.core.annotation.GenerationMode;
import com.chaykin.jasper.core.annotation.PageOrientation;

public record TemplateSpec(GenerationMode mode,
                           String templatePath,
                           PageOrientation orientation,
                           boolean subreport) {

}
