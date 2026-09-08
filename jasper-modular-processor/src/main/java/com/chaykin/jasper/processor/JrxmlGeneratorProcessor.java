package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.CollectionComponentType;
import com.chaykin.jasper.core.annotation.GenerationMode;
import com.chaykin.jasper.core.annotation.JasperCollection;
import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.annotation.PageOrientation;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;
import com.chaykin.jasper.processor.model.JrxmlDataset;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.beans.Introspector;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Annotation processor that generates and updates JRXML report templates at compile time.
 */
@SupportedAnnotationTypes({
        "com.chaykin.jasper.core.annotation.JasperModularReport",
        "com.chaykin.jasper.core.annotation.JasperSubreport"
})
public class JrxmlGeneratorProcessor extends AbstractProcessor {

    private static final String GET_PREFIX = "get";

    private static final String IS_PREFIX = "is";

    private static final String JR_BEAN_COLLECTION_DS =
            "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource";

    private static final Set<String> SIMPLE_TYPES = Set.of(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Number",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.util.UUID",
            "java.util.Locale",
            "java.util.Currency",
            "java.net.URI",
            "java.net.URL");

    private Filer filer;

    private Messager messager;

    private Elements elementUtils;

    private Types typeUtils;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

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
                error("@JasperModularReport/@JasperSubreport is not supported on "
                      + element.getKind().toString().toLowerCase()
                      + " - only classes extending ModularReport/SubreportModule"
                      + " are supported",
                      element);
                continue;
            }

            TypeElement classElement = (TypeElement) element;
            boolean isRoot = isDirectlyAnnotated(classElement, JasperModularReport.class);
            boolean isSubreport = isDirectlyAnnotated(classElement, JasperSubreport.class);
            if (!isRoot && !isSubreport) {
                continue;
            }
            if (isRoot && isSubreport) {
                error("A class cannot be annotated with both @JasperModularReport and "
                      + "@JasperSubreport: " + classElement.getSimpleName(),
                      classElement);
                continue;
            }
            if (!isModularDataFillerSubtype(classElement.asType())) {
                error("Annotated report classes must extend ModularReport or SubreportModule: "
                      + classElement.getSimpleName(), classElement);
                continue;
            }
            try {
                generate(classElement);
            } catch (Exception e) {
                error("Failed to generate JRXML for "
                      + classElement.getSimpleName() + ": " + e,
                      classElement);
            }
        }
    }

    private void generate(TypeElement classElement) throws IOException, JRException {
        GenerationMode mode = resolveMode(classElement);

        if (mode == GenerationMode.NONE) {
            note("Skipping generation for: " + classElement.getSimpleName());
            return;
        }

        String templatePath = resolveTemplatePath(classElement);
        List<JrxmlParameter> fields = describeFields(classElement);

        note("Generating JRXML [" + mode + "] for: "
             + classElement.getSimpleName() + " -> " + templatePath);

        JasperDesign design = switch (mode) {
            case CREATE -> createEmptyDesign(classElement);
            case INJECT -> resolveDesign(templatePath, classElement);
            default -> throw new IllegalStateException("Unreachable: mode=" + mode);
        };

        writeOutput(classElement, templatePath, design, fields);
    }

    private void writeOutput(TypeElement classElement,
                             String templatePath,
                             JasperDesign design,
                             List<JrxmlParameter> fields) throws IOException, JRException {
        FileObject output;
        try {
            output = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                                          "",
                                          templatePath.replaceFirst("^/", ""));
        } catch (FilerException e) {
            error("Template path '" + templatePath + "' is already generated - two report "
                  + "classes cannot share one template. Give each class its own templatePath.",
                  classElement);
            return;
        }

        try (OutputStream out = output.openOutputStream()) {
            new JrxmlTemplateInjector(messager).inject(design, fields, out);
        }
    }

    /**
     * Loads the existing JRXML via {@link #findExistingTemplate}, or falls back to a blank
     * design with a warning.
     */
    private JasperDesign resolveDesign(String templatePath,
                                       TypeElement classElement) throws IOException, JRException {
        try (InputStream stream = findExistingTemplate(templatePath)) {
            if (stream != null) {
                note("Injecting into existing: " + templatePath);
                return JRXmlLoader.load(stream);
            }
        }
        warn("Existing template not found - generating a blank skeleton: "
             + templatePath
             + ". If the template exists, the build did not expose it to the "
             + "annotation processor; do not copy the skeleton over your design.");
        return createEmptyDesign(classElement);
    }

    /**
     * Looks for the template in the compiled-classes output first, then on the processor classpath.
     */
    private InputStream findExistingTemplate(String templatePath) {
        String path = templatePath.replaceFirst("^/", "");
        try {
            return filer.getResource(StandardLocation.CLASS_OUTPUT, "", path).openInputStream();
        } catch (IOException | RuntimeException ignored) {
            // not present in class output - fall back to the processor classpath
        }
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    private JasperDesign createEmptyDesign(TypeElement classElement) {
        boolean isSubreport = classElement.getAnnotation(JasperSubreport.class) != null;
        boolean isLandscape = resolveOrientation(classElement) == PageOrientation.LANDSCAPE;

        JasperDesign design = new JasperDesign();
        design.setName(isLandscape ? "Blank_A4_Landscape" : "Blank_A4");

        if (isLandscape) {
            design.setPageWidth(842);
            design.setPageHeight(595);
            design.setColumnWidth(802);
            design.setOrientation(OrientationEnum.LANDSCAPE);
        } else {
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setColumnWidth(555);
        }

        int margin = isSubreport ? 0 : 20;
        design.setLeftMargin(margin);
        design.setRightMargin(margin);
        design.setTopMargin(margin);
        design.setBottomMargin(margin);

        design.setBackground(emptyBand(0));
        design.setTitle(emptyBand(20));
        design.setPageHeader(emptyBand(20));
        design.setColumnHeader(emptyBand(20));
        ((JRDesignSection) design.getDetailSection()).addBand(emptyBand(100));
        design.setColumnFooter(emptyBand(20));
        design.setPageFooter(emptyBand(20));
        design.setSummary(emptyBand(20));

        return design;
    }

    private static JRDesignBand emptyBand(int height) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(height);
        band.setSplitType(SplitTypeEnum.STRETCH);
        return band;
    }

    private List<JrxmlParameter> describeFields(TypeElement classElement) {
        List<JrxmlParameter> result = new ArrayList<>();
        forEachField(classElement,
                     this::isJasperModularDataFiller,
                     f -> describeField(f, result));
        reportDuplicatePrefixes(classElement, result);
        return result;
    }

    private void reportDuplicatePrefixes(TypeElement classElement, List<JrxmlParameter> fields) {
        Set<String> seen = new HashSet<>();
        fields.stream()
              .filter(f -> f.subreportPrefix() != null)
              .filter(f -> f.name().equals(f.subreportPrefix() + "Report")
                           || f.name().equals(f.subreportPrefix() + "DataSource"))
              .map(JrxmlParameter::subreportPrefix)
              .filter(prefix -> !seen.add(prefix))
              .distinct()
              .forEach(prefix -> error(
                      "Duplicate subreport prefix '" + prefix + "'. Two subreport fields resolve "
                      + "to the same parameter names, so one of them would be lost. Give one a "
                      + "distinct prefix, for example a subclass annotated "
                      + "@JasperSubreport(templatePath = ..., prefix = \"Other\").",
                      classElement));
    }

    /**
     * Walks the class hierarchy upward, applying {@code action} to every non-{@link JasperIgnore}
     * field, until {@code stopAt} matches or the hierarchy ends.
     */
    private void forEachField(TypeElement start,
                              Predicate<TypeElement> stopAt,
                              Consumer<VariableElement> action) {
        TypeElement current = start;
        while (current != null && !stopAt.test(current)) {
            ElementFilter.fieldsIn(current.getEnclosedElements())
                         .stream()
                         .filter(f -> f.getAnnotation(JasperIgnore.class) == null)
                         .forEach(action);

            current = asTypeElement(current.getSuperclass());
        }
    }

    private void describeField(VariableElement field, List<JrxmlParameter> result) {
        TypeElement fieldClass = asTypeElement(field.asType());

        JasperSubreport subreportAnnotation = fieldClass != null
                                              ? fieldClass.getAnnotation(JasperSubreport.class)
                                              : null;
        if (subreportAnnotation != null) {
            result.addAll(describeSubreportParameters(fieldClass, subreportAnnotation));
            return;
        }

        if (isModularDataFillerSubtype(field.asType())) {
            error("Subreport fields must be declared with a @JasperSubreport-annotated type. Field: "
                  + field.getSimpleName(), field);
            return;
        }

        if (isCollection(field.asType())) {
            describeCollectionField(field, result);
            return;
        }

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      resolveJrxmlClass(field.asType()),
                                      null));
    }

    private List<JrxmlParameter> describeSubreportParameters(TypeElement fieldClass,
                                                             JasperSubreport annotation) {
        String prefix = annotation.prefix().isEmpty()
                        ? fieldClass.getSimpleName().toString()
                        : annotation.prefix();
        return List.of(
                new JrxmlParameter(prefix + "Report",
                                   "net.sf.jasperreports.engine.JasperReport",
                                   null,
                                   prefix),
                new JrxmlParameter(prefix + "MapParameter",
                                   "java.util.Map",
                                   null)
        );
    }

    private void describeCollectionField(VariableElement field, List<JrxmlParameter> result) {
        TypeMirror elementType = resolveCollectionElementType(field.asType());
        if (elementType == null) {
            warn("Collection field has no resolvable element type - nothing generated for: "
                 + field.getSimpleName()
                 + ". Declare a concrete element type, for example List<LineItem>.");
            return;
        }

        TypeElement elementClass = asTypeElement(elementType);

        if (elementClass != null && elementClass.getKind() == ElementKind.RECORD) {
            error("Records are not supported as collection elements - JasperReports"
                  + " bean data sources require JavaBean getters. Field: "
                  + field.getSimpleName(),
                  field);
            return;
        }

        JasperSubreport subreportAnnotation = elementClass != null
                                              ? elementClass.getAnnotation(JasperSubreport.class)
                                              : null;
        if (subreportAnnotation != null) {
            describeSubreportListField(elementClass, subreportAnnotation, result);
            return;
        }

        if (isModularDataFillerSubtype(elementType)) {
            error("Collection elements that are modular reports must be annotated "
                  + "with @JasperSubreport. Field: " + field.getSimpleName(),
                  field);
            return;
        }

        JasperCollection collectionAnn = field.getAnnotation(JasperCollection.class);
        CollectionComponentType componentType = collectionAnn != null
                                                ? collectionAnn.type()
                                                : CollectionComponentType.TABLE;
        int columnWidth = collectionAnn != null
                          ? collectionAnn.columnWidth()
                          : JasperCollection.DEFAULT_COLUMN_WIDTH;

        JrxmlDataset dataset = elementClass != null
                               ? describeDataset(field.getSimpleName().toString(),
                                                 elementClass,
                                                 componentType,
                                                 columnWidth)
                               : null;

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      JR_BEAN_COLLECTION_DS,
                                      dataset));
    }

    /**
     * Describes a collection of {@link JasperSubreport} modules as a repeating subreport.
     */
    private void describeSubreportListField(TypeElement elementClass,
                                            JasperSubreport annotation,
                                            List<JrxmlParameter> result) {
        String prefix = annotation.prefix().isEmpty()
                        ? elementClass.getSimpleName().toString()
                        : annotation.prefix();

        JrxmlDataset dataset = new JrxmlDataset(
                prefix + "Dataset",
                List.of(new JrxmlDatasetField(JasperModularDataFiller.SUBREPORT_PARAMS_FIELD,
                                              "java.util.Map"),
                        new JrxmlDatasetField(JasperModularDataFiller.SUBREPORT_REPORT_FIELD,
                                              "net.sf.jasperreports.engine.JasperReport")),
                CollectionComponentType.LIST,
                JasperCollection.DEFAULT_COLUMN_WIDTH);

        result.add(new JrxmlParameter(prefix + "DataSource",
                                      "net.sf.jasperreports.engine.JRDataSource",
                                      dataset,
                                      prefix));
    }

    private JrxmlDataset describeDataset(String name,
                                         TypeElement elementClass,
                                         CollectionComponentType componentType,
                                         int columnWidth) {
        if (isSimpleType(elementClass)) {
            return new JrxmlDataset(name,
                                    List.of(new JrxmlDatasetField(
                                            JRAbstractBeanDataSource.CURRENT_BEAN_MAPPING,
                                            elementClass.getQualifiedName().toString())),
                                    componentType,
                                    columnWidth);
        }

        Map<String, JrxmlDatasetField> fields = new LinkedHashMap<>();
        forEachField(elementClass,
                     t -> t.getQualifiedName().contentEquals("java.lang.Object"),
                     f -> {
                         ExecutableElement accessor = findAccessor(f);
                         if (f.getModifiers().contains(Modifier.STATIC) && accessor == null) {
                             return;
                         }
                         String property = propertyName(f, accessor);
                         fields.putIfAbsent(property,
                                            new JrxmlDatasetField(property,
                                                                  resolveJrxmlClass(f.asType())));
                     });

        return new JrxmlDataset(name,
                                List.copyOf(fields.values()),
                                componentType,
                                columnWidth);
    }

    private String propertyName(VariableElement field, ExecutableElement accessor) {
        String getter = accessor != null
                        ? accessor.getSimpleName().toString()
                        : getterNames(field).get(0);
        String prefix = getter.startsWith(IS_PREFIX) ? IS_PREFIX : GET_PREFIX;
        return Introspector.decapitalize(getter.substring(prefix.length()));
    }

    private GenerationMode resolveMode(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        return root != null
               ? root.mode()
               : Objects.requireNonNull(classElement.getAnnotation(JasperSubreport.class)).mode();
    }

    private String resolveTemplatePath(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        return root != null
               ? root.templatePath()
               : Objects.requireNonNull(classElement.getAnnotation(JasperSubreport.class)).templatePath();
    }

    private PageOrientation resolveOrientation(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        return root != null
               ? root.orientation()
               : Objects.requireNonNull(classElement.getAnnotation(JasperSubreport.class)).orientation();
    }

    private boolean isCollection(TypeMirror type) {
        TypeElement collection = elementUtils.getTypeElement("java.util.Collection");
        return typeUtils.isAssignable(typeUtils.erasure(type),
                                      typeUtils.erasure(collection.asType()));
    }

    private boolean isSimpleType(TypeElement element) {
        if (element.getKind() == ElementKind.ENUM) {
            return true;
        }
        String name = element.getQualifiedName().toString();
        return SIMPLE_TYPES.contains(name) || name.startsWith("java.time.");
    }

    private ExecutableElement findAccessor(VariableElement field) {
        if (!(field.getEnclosingElement() instanceof TypeElement owner)) {
            return null;
        }
        List<String> getters = getterNames(field);
        return ElementFilter.methodsIn(elementUtils.getAllMembers(owner))
                            .stream()
                            .filter(m -> m.getParameters().isEmpty()
                                         && m.getModifiers().contains(Modifier.PUBLIC)
                                         && !m.getModifiers().contains(Modifier.STATIC)
                                         && getters.contains(m.getSimpleName().toString()))
                            .findFirst()
                            .orElse(null);
    }

    private List<String> getterNames(VariableElement field) {
        String name = field.getSimpleName().toString();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        if (field.asType().getKind() != TypeKind.BOOLEAN) {
            return List.of(GET_PREFIX + capitalized);
        }
        boolean alreadyPrefixed = name.length() > IS_PREFIX.length()
                                  && name.startsWith(IS_PREFIX)
                                  && Character.isUpperCase(name.charAt(IS_PREFIX.length()));
        return alreadyPrefixed
               ? List.of(name, GET_PREFIX + capitalized)
               : List.of(IS_PREFIX + capitalized, GET_PREFIX + capitalized);
    }

    private boolean isDirectlyAnnotated(TypeElement classElement,
                                        Class<? extends Annotation> annotation) {
        String name = annotation.getCanonicalName();
        return classElement.getAnnotationMirrors()
                           .stream()
                           .map(m -> asTypeElement(m.getAnnotationType()))
                           .filter(Objects::nonNull)
                           .anyMatch(t -> t.getQualifiedName().contentEquals(name));
    }

    private TypeElement asTypeElement(TypeMirror type) {
        return typeUtils.asElement(type) instanceof TypeElement typeElement ? typeElement : null;
    }

    private boolean isModularDataFillerSubtype(TypeMirror type) {
        TypeElement filler = elementUtils.getTypeElement(
                "com.chaykin.jasper.core.contract.JasperModularDataFiller");
        return filler != null && typeUtils.isAssignable(typeUtils.erasure(type),
                                                        typeUtils.erasure(filler.asType()));
    }

    private boolean isJasperModularDataFiller(TypeElement element) {
        return element.getQualifiedName()
                      .toString()
                      .equals("com.chaykin.jasper.core.contract.JasperModularDataFiller");
    }

    private TypeMirror resolveCollectionElementType(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) {
            return null;
        }

        List<? extends TypeMirror> args = declaredType.getTypeArguments();
        if (args.isEmpty()) {
            return null;
        }

        TypeMirror arg = args.get(0);
        return arg instanceof WildcardType wildcardType
               ? (wildcardType.getExtendsBound() != null
                  ? wildcardType.getExtendsBound()
                  : wildcardType.getSuperBound())
               : arg;
    }

    /**
     * Erases generics and boxes primitives for use as a JRXML {@code class} attribute.
     */
    private String resolveJrxmlClass(TypeMirror typeMirror) {
        if (typeMirror.getKind().isPrimitive()) {
            return typeUtils.boxedClass((PrimitiveType) typeMirror)
                            .getQualifiedName()
                            .toString();
        }
        return typeUtils.erasure(typeMirror).toString();
    }

    private void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    private void warn(String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message);
    }

    private void error(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
