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
import com.chaykin.jasper.processor.model.TemplateSpec;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.chaykin.jasper.core.contract.JasperModularDataFiller.DATASET_SUFFIX;
import static com.chaykin.jasper.core.contract.JasperModularDataFiller.DATA_SOURCE_SUFFIX;
import static com.chaykin.jasper.core.contract.JasperModularDataFiller.MAP_PARAMETER_SUFFIX;
import static com.chaykin.jasper.core.contract.JasperModularDataFiller.REPORT_SUFFIX;
import static java.util.Objects.requireNonNull;

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

    private static final String JASPER_REPORT_CLASS = JasperReport.class.getName();

    private static final String MAP_CLASS = Map.class.getName();

    private static final String DATA_SOURCE_CLASS = JRDataSource.class.getName();

    private static final Map<String, String> WIRING_CLASSES = Map.of(
            REPORT_SUFFIX,
            JASPER_REPORT_CLASS,
            MAP_PARAMETER_SUFFIX,
            MAP_CLASS,
            DATA_SOURCE_SUFFIX,
            DATA_SOURCE_CLASS);

    private static final Set<String> JDK_PACKAGES = Set.of("java.", "javax.", "jdk.", "sun.");

    private static final String JR_BEAN_COLLECTION_DS = JRBeanCollectionDataSource.class.getName();

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

        processAnnotated(roundEnv.getElementsAnnotatedWith(JasperModularReport.class),
                         JasperModularReport.class);
        processAnnotated(roundEnv.getElementsAnnotatedWith(JasperSubreport.class),
                         JasperSubreport.class);

        return true;
    }

    private void processAnnotated(Set<? extends Element> elements,
                                  Class<? extends Annotation> annotation) {
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

            if (isRoot && isSubreport) {
                error("A class cannot be annotated with both @JasperModularReport and "
                      + "@JasperSubreport: " + classElement.getSimpleName(),
                      classElement);
                continue;
            }

            if (!isDirectlyAnnotated(classElement, annotation)) {
                continue;
            }

            if (!isModularDataFillerSubtype(classElement.asType())) {
                error("Annotated report classes must extend ModularReport or SubreportModule: "
                      + classElement.getSimpleName(), classElement);
                continue;
            }

            try {
                generate(classElement, isSubreport);
            } catch (Exception e) {
                error("Failed to generate JRXML for "
                      + classElement.getSimpleName() + ": " + e,
                      classElement);
            }
        }
    }

    private void generate(TypeElement classElement, boolean subreport) throws IOException,
                                                                              JRException {
        TemplateSpec spec = resolveTemplateSpec(classElement, subreport);
        GenerationMode mode = spec.mode();

        if (mode == GenerationMode.NONE) {
            note("Skipping generation for: " + classElement.getSimpleName());
            return;
        }

        String templatePath = spec.templatePath();
        List<JrxmlParameter> fields = describeFields(classElement);

        note("Generating JRXML [" + mode + "] for: "
             + classElement.getSimpleName() + " -> " + templatePath);

        JasperDesign design = switch (mode) {
            case CREATE -> createEmptyDesign(spec);
            case INJECT -> resolveDesign(spec);
            default -> throw new IllegalStateException("Unreachable: mode=" + mode);
        };

        verifyTemplate(classElement, design, fields);

        writeOutput(classElement, templatePath, design, fields);
    }

    private void verifyTemplate(TypeElement classElement,
                                JasperDesign design,
                                List<JrxmlParameter> fields) {
        Map<String, String> generated = fields.stream()
                                              .collect(Collectors.toMap(JrxmlParameter::name,
                                                                        JrxmlParameter::jrxmlClass,
                                                                        (first, second) -> first));
        Set<String> orphans = new LinkedHashSet<>();

        for (JRParameter parameter: design.getParametersList()) {
            if (parameter.isSystemDefined()) {
                continue;
            }
            String expectedClass = generated.get(parameter.getName());

            if (expectedClass == null) {
                if (isGeneratedParameter(parameter)) {
                    orphans.add(parameter.getName());
                    error("Template declares '" + parameter.getName() + "' but no field of "
                          + classElement.getSimpleName() + " produces it. If the field was "
                          + "renamed, rename the parameter and its $P{" + parameter.getName()
                          + "} references in the template; if it was removed, delete the "
                          + "parameter and the element that uses it.",
                          classElement);
                }
            } else if (!expectedClass.equals(parameter.getValueClassName())) {
                error("Template declares '" + parameter.getName() + "' as "
                      + parameter.getValueClassName() + ", but the field produces "
                      + expectedClass + " - update the class in the template.",
                      classElement);
            }
        }

        Set<String> generatedDatasets = fields.stream()
                                              .map(JrxmlParameter::dataset)
                                              .filter(Objects::nonNull)
                                              .map(JrxmlDataset::name)
                                              .collect(Collectors.toSet());

        Set<String> orphanDatasets = orphans.stream()
                                            .map(name -> name.endsWith(DATA_SOURCE_SUFFIX)
                                                         ? name.substring(0, name.length()
                                                                             - DATA_SOURCE_SUFFIX.length())
                                                           + DATASET_SUFFIX
                                                         : name)
                                            .collect(Collectors.toSet());

        design.getDatasetMap()
              .keySet()
              .stream()
              .filter(name -> !generatedDatasets.contains(name))
              .filter(orphanDatasets::contains)
              .forEach(name -> error("Template declares dataset '" + name + "' but no field of "
                                     + classElement.getSimpleName() + " produces it. If the field "
                                     + "was renamed, rename the dataset and the component that "
                                     + "runs it; if it was removed, delete both.",
                                     classElement));
    }

    private boolean isGeneratedParameter(JRParameter parameter) {
        if (JR_BEAN_COLLECTION_DS.equals(parameter.getValueClassName())) {
            return true;
        }
        return WIRING_CLASSES.entrySet()
                             .stream()
                             .anyMatch(e -> parameter.getName().endsWith(e.getKey())
                                            && e.getValue().equals(parameter.getValueClassName()));
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
    private JasperDesign resolveDesign(TemplateSpec spec) throws IOException,
                                                                 JRException {
        try (InputStream stream = findExistingTemplate(spec.templatePath())) {
            if (stream != null) {
                note("Injecting into existing: " + spec.templatePath());
                return JRXmlLoader.load(stream);
            }
        }
        warn("Existing template not found - generating a blank skeleton: "
             + spec.templatePath()
             + ". If the template exists, the build did not expose it to the "
             + "annotation processor; do not copy the skeleton over your design.");
        return createEmptyDesign(spec);
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

    private JasperDesign createEmptyDesign(TemplateSpec spec) {
        boolean isLandscape = spec.orientation() == PageOrientation.LANDSCAPE;

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

        int margin = spec.subreport() ? 0 : 20;
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
        reportDuplicateSubreportParameters(classElement, result);
        return result;
    }

    private void reportDuplicateSubreportParameters(TypeElement classElement,
                                                    List<JrxmlParameter> fields) {
        Set<String> seen = new HashSet<>();

        for (JrxmlParameter field: fields) {
            if (field.subreportPrefix() != null && !seen.add(field.name())) {
                error("Duplicate subreport parameter '" + field.name()
                      + "'. Two fields resolve to the same name, so one of them would be lost. "
                      + "Rename one of the fields.",
                      classElement);
            }
        }
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

        if (fieldClass != null && fieldClass.getAnnotation(JasperSubreport.class) != null) {
            result.addAll(describeSubreportParameters(field.getSimpleName().toString()));
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

    private List<JrxmlParameter> describeSubreportParameters(String prefix) {
        return List.of(
                new JrxmlParameter(prefix + REPORT_SUFFIX,
                                   JASPER_REPORT_CLASS,
                                   null,
                                   prefix),
                new JrxmlParameter(prefix + MAP_PARAMETER_SUFFIX,
                                   MAP_CLASS,
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

        if (elementClass != null && elementClass.getAnnotation(JasperSubreport.class) != null) {
            describeSubreportListField(field.getSimpleName().toString(), result);
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

        if (elementClass != null && dataset == null) {
            warn("Collection element type " + elementClass.getSimpleName() + " exposes no "
                 + "readable properties - no dataset or component generated for: "
                 + field.getSimpleName() + ". Give the type JavaBean getters or fields.",
                 field);
        }

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      JR_BEAN_COLLECTION_DS,
                                      dataset));
    }

    /**
     * Describes a collection of {@link JasperSubreport} modules as a repeating subreport.
     */
    private void describeSubreportListField(String prefix, List<JrxmlParameter> result) {
        JrxmlDataset dataset = new JrxmlDataset(
                prefix + DATASET_SUFFIX,
                List.of(new JrxmlDatasetField(JasperModularDataFiller.SUBREPORT_PARAMS_FIELD,
                                              MAP_CLASS),
                        new JrxmlDatasetField(JasperModularDataFiller.SUBREPORT_REPORT_FIELD,
                                              JASPER_REPORT_CLASS)),
                CollectionComponentType.LIST,
                JasperCollection.DEFAULT_COLUMN_WIDTH);

        result.add(new JrxmlParameter(prefix + DATA_SOURCE_SUFFIX,
                                      DATA_SOURCE_CLASS,
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
        if (elementClass.getKind() == ElementKind.INTERFACE) {
            describeAccessorFields(elementClass, fields);
        } else {
            forEachField(elementClass,
                         t -> t.getQualifiedName().contentEquals(Object.class.getCanonicalName()),
                         f -> {
                             ExecutableElement accessor = findAccessor(f);
                             if (f.getModifiers().contains(Modifier.STATIC) && accessor == null) {
                                 return;
                             }
                             String property = propertyName(f, accessor);
                             TypeMirror propertyType = accessor != null
                                                       ? accessor.getReturnType()
                                                       : f.asType();
                             fields.putIfAbsent(property,
                                                new JrxmlDatasetField(property,
                                                                      resolveJrxmlClass(propertyType)));
                         });
        }

        if (fields.isEmpty()) {
            return null;
        }

        return new JrxmlDataset(name,
                                List.copyOf(fields.values()),
                                componentType,
                                columnWidth);
    }

    private void describeAccessorFields(TypeElement elementClass,
                                        Map<String, JrxmlDatasetField> fields) {
        ElementFilter.methodsIn(elementUtils.getAllMembers(elementClass))
                     .stream()
                     .filter(this::isAccessor)
                     .forEach(accessor -> {
                         String property = propertyName(accessor.getSimpleName().toString());
                         fields.putIfAbsent(property,
                                            new JrxmlDatasetField(
                                                    property,
                                                    resolveJrxmlClass(accessor.getReturnType())));
                     });
    }

    private boolean isAccessor(ExecutableElement method) {
        if (!method.getParameters().isEmpty()
            || !method.getModifiers().contains(Modifier.PUBLIC)
            || method.getModifiers().contains(Modifier.STATIC)
            || method.getSimpleName().contentEquals("getClass")) {
            return false;
        }

        String name = method.getSimpleName().toString();
        if (name.startsWith(GET_PREFIX) && name.length() > GET_PREFIX.length()) {
            return method.getReturnType().getKind() != TypeKind.VOID;
        }
        return name.startsWith(IS_PREFIX)
               && name.length() > IS_PREFIX.length()
               && method.getReturnType().getKind() == TypeKind.BOOLEAN;
    }

    private String propertyName(VariableElement field, ExecutableElement accessor) {
        return propertyName(accessor != null
                            ? accessor.getSimpleName().toString()
                            : getterNames(field).get(0));
    }

    private String propertyName(String getter) {
        String prefix = getter.startsWith(IS_PREFIX) ? IS_PREFIX : GET_PREFIX;
        return Introspector.decapitalize(getter.substring(prefix.length()));
    }

    private TemplateSpec resolveTemplateSpec(TypeElement typeElement, boolean isSubreport) {
        if (isSubreport) {
            JasperSubreport annotation =
                    requireNonNull(typeElement.getAnnotation(JasperSubreport.class));
            return new TemplateSpec(annotation.mode(),
                                    annotation.templatePath(),
                                    annotation.orientation(),
                                    true);
        }

        JasperModularReport annotation = requireNonNull(typeElement.getAnnotation(JasperModularReport.class));
        return new TemplateSpec(annotation.mode(),
                                annotation.templatePath(),
                                annotation.orientation(),
                                false);
    }

    private boolean isCollection(TypeMirror type) {
        return isAssignableTo(type, Collection.class);
    }

    private boolean isSimpleType(TypeElement element) {
        if (element.getKind() == ElementKind.ENUM) {
            return true;
        }
        String name = element.getQualifiedName().toString();
        return JDK_PACKAGES.stream().anyMatch(name::startsWith);
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
                            .min(Comparator.comparingInt(
                                    m -> getters.indexOf(m.getSimpleName().toString())))
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
        return isAssignableTo(type, JasperModularDataFiller.class);
    }

    private boolean isAssignableTo(TypeMirror type, Class<?> target) {
        TypeElement targetElement = elementUtils.getTypeElement(target.getCanonicalName());
        return targetElement != null
               && typeUtils.isAssignable(typeUtils.erasure(type),
                                         typeUtils.erasure(targetElement.asType()));
    }

    private boolean isJasperModularDataFiller(TypeElement element) {
        return element.getQualifiedName()
                      .contentEquals(JasperModularDataFiller.class.getCanonicalName());
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

    private void warn(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private void error(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
