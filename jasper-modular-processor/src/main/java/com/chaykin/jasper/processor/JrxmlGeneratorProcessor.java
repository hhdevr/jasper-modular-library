package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.GenerationMode;
import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.processor.model.JrxmlDataset;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes({
        "com.chaykin.jasper.core.annotation.JasperRootReport",
        "com.chaykin.jasper.core.annotation.JasperSubreport"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
@SupportedOptions("jasper.template")
public class JrxmlGeneratorProcessor extends AbstractProcessor {

    private static final String JR_BEAN_COLLECTION_DS =
            "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource";

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.filer = env.getFiler();
        this.messager = env.getMessager();
        this.elementUtils = env.getElementUtils();
        this.typeUtils = env.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        processAnnotated(roundEnv.getElementsAnnotatedWith(JasperModularReport.class));
        processAnnotated(roundEnv.getElementsAnnotatedWith(JasperSubreport.class));

        return true;
    }

    private void processAnnotated(Set<? extends Element> elements) {
        for (Element element: elements) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }

            TypeElement classElement = (TypeElement) element;
            try {
                generate(classElement);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                                      "Failed to generate JRXML for "
                                      + classElement.getSimpleName() + ": " + e.getMessage(),
                                      classElement);
            }
        }
    }

    private void generate(TypeElement classElement) throws Exception {
        GenerationMode mode = resolveMode(classElement);

        if (mode == GenerationMode.NONE) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Skipping generation for: " + classElement.getSimpleName());
            return;
        }

        String templatePath = resolveTemplatePath(classElement);
        List<JrxmlParameter> fields = describeFields(classElement);

        messager.printMessage(Diagnostic.Kind.NOTE,
                              "Generating JRXML [" + mode + "] for: "
                              + classElement.getSimpleName() + " → " + templatePath);

        try (InputStream source = switch (mode) {
            case CREATE -> loadDefaultTemplate();
            case INJECT -> loadExistingOrDefault(templatePath);
            default -> throw new IllegalStateException("Unreachable: mode=" + mode);
        }) {
            writeOutput(templatePath, source, fields);
        }
    }

    private void writeOutput(String templatePath,
                             InputStream source,
                             List<JrxmlParameter> fields) throws Exception {
        FileObject output = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                                                 "",
                                                 templatePath.replaceFirst("^/", "")
        );

        try (OutputStream out = output.openOutputStream()) {
            new JrxmlTemplateInjector(messager).inject(source, fields, out);
        }
    }

    private List<JrxmlParameter> describeFields(TypeElement classElement) {
        List<JrxmlParameter> result = new ArrayList<>();
        TypeElement current = classElement;

        while (current != null && !isJasperModularDataFiller(current)) {
            for (VariableElement field: ElementFilter.fieldsIn(current.getEnclosedElements())) {

                if (field.getAnnotation(JasperIgnore.class) != null) {
                    continue;
                }

                TypeElement fieldClass = (TypeElement)
                        typeUtils.asElement(field.asType());

                if (fieldClass != null) {
                    JasperSubreport ann = fieldClass.getAnnotation(JasperSubreport.class);

                    if (ann != null) {
                        String prefix = ann.prefix().isEmpty()
                                        ? fieldClass.getSimpleName().toString()
                                        : ann.prefix();
                        result.add(new JrxmlParameter(prefix + "Report",
                                                      "net.sf.jasperreports.engine.JasperReport",
                                                      null));
                        result.add(new JrxmlParameter(prefix + "MapParameter",
                                                      "java.util.Map",
                                                      null));
                        continue;
                    }
                }

                if (isCollection(field.asType())) {
                    TypeMirror elementType = resolveCollectionElementType(field.asType());
                    if (elementType != null) {
                        TypeElement elementClass = (TypeElement) typeUtils.asElement(elementType);

                        if (elementClass != null && !isSimpleType(elementClass)) {
                            result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                                          JR_BEAN_COLLECTION_DS,
                                                          describeDataset(field.getSimpleName().toString(),
                                                                          elementClass)));
                        } else {
                            result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                                          JR_BEAN_COLLECTION_DS,
                                                          null));
                        }
                    }
                    continue;
                }

                result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                              field.asType().toString(),
                                              null));
            }

            TypeMirror superclass = current.getSuperclass();
            current = superclass != null
                      ? (TypeElement) typeUtils.asElement(superclass)
                      : null;
        }
        return result;
    }

    private JrxmlDataset describeDataset(String name, TypeElement elementClass) {
        List<JrxmlDatasetField> fields = new ArrayList<>();

        for (VariableElement field: ElementFilter.fieldsIn(elementClass.getEnclosedElements())) {
            if (field.getAnnotation(JasperIgnore.class) != null) {
                continue;
            }

            fields.add(new JrxmlDatasetField(field.getSimpleName().toString(),
                                             field.asType().toString()
            ));
        }
        return new JrxmlDataset(name, fields);
    }

    private GenerationMode resolveMode(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        if (root != null) {
            return root.mode();
        }

        JasperSubreport sub = classElement.getAnnotation(JasperSubreport.class);
        if (sub != null) {
            return sub.mode();
        }

        return GenerationMode.INJECT;
    }

    private String resolveTemplatePath(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        if (root != null) {
            return root.templatePath();
        }

        JasperSubreport sub = classElement.getAnnotation(JasperSubreport.class);
        if (sub != null) {
            return sub.templatePath();
        }

        return "reports/" + toSnakeCase(classElement.getSimpleName().toString()) + ".jrxml";
    }

    private InputStream loadDefaultTemplate() {
        String templateName = resolveTemplateName();
        InputStream stream = getClass()
                .getResourceAsStream("/default-empty-reports/" + templateName);

        if (stream == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                                  "Default template not found: " + templateName);
        }
        return stream;
    }

    private InputStream loadExistingOrDefault(String templatePath) {
        String path = templatePath.replaceFirst("^/", "");
        try {
            FileObject existing = filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
            InputStream stream = existing.openInputStream();
            messager.printMessage(Diagnostic.Kind.NOTE, "Injecting into existing: " + templatePath);
            return stream;
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Not found - creating from template: " + templatePath);
            return loadDefaultTemplate();
        }
    }

    private String resolveTemplateName() {
        String option = processingEnv.getOptions()
                                     .getOrDefault("jasper.template", "A4");
        return "Blank_" + option + ".jrxml";
    }

    private boolean isCollection(TypeMirror type) {
        TypeElement collection = elementUtils.getTypeElement("java.util.Collection");
        return typeUtils.isAssignable(typeUtils.erasure(type),
                                      typeUtils.erasure(collection.asType())
        );
    }

    private boolean isSimpleType(TypeElement element) {
        String name = element.getQualifiedName().toString();
        return name.equals("java.lang.String")
               || name.equals("java.lang.Integer")
               || name.equals("java.lang.Long")
               || name.equals("java.lang.Double")
               || name.equals("java.lang.Float")
               || name.equals("java.lang.Boolean")
               || name.equals("java.lang.Short")
               || name.equals("java.lang.Byte")
               || name.equals("java.lang.Character")
               || name.equals("java.math.BigDecimal")
               || name.equals("java.math.BigInteger")
               || name.startsWith("java.time.");
    }

    private boolean isJasperModularDataFiller(TypeElement element) {
        return element.getQualifiedName()
                      .toString()
                      .equals("com.chaykin.jasper.core.contract.JasperModularDataFiller");
    }

    private TypeMirror resolveCollectionElementType(TypeMirror type) {
        if (type instanceof DeclaredType declaredType) {
            List<? extends TypeMirror> args = declaredType.getTypeArguments();
            if (!args.isEmpty()) {
                return args.getFirst();
            }
        }
        return null;
    }

    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}