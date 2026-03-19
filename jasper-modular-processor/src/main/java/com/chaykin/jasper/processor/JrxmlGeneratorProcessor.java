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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
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

/**
 * Annotation processor that generates and updates JRXML report templates at compile time.
 *
 * <p>This processor is triggered by the {@link JasperModularReport} and
 * {@link JasperSubreport} annotations. For each annotated class it inspects the declared
 * fields, builds a model of parameters, datasets and subreport references, and either
 * creates a new JRXML file from a blank template or injects missing elements into an
 * existing one - depending on the {@link GenerationMode} specified on the annotation.</p>
 *
 * <h2>Generation modes</h2>
 * <ul>
 *   <li>{@link GenerationMode#CREATE} - generates a new JRXML from the built-in blank
 *       template, pre-populated with all parameters, datasets, list components and
 *       subreport bands derived from the class fields.</li>
 *   <li>{@link GenerationMode#INJECT} - reads the existing JRXML and injects only the
 *       elements that are not already present, preserving all user-defined layout.</li>
 *   <li>{@link GenerationMode#NONE} - skips generation entirely for this class.</li>
 * </ul>
 *
 * <h2>Compiler option</h2>
 * <p>The blank template page size can be controlled via the {@code jasper.template}
 * annotation processor option:</p>
 * <pre>{@code
 * <compilerArgs>
 *     <arg>-Ajasper.template=A4</arg>
 * </compilerArgs>
 * }</pre>
 * <p>Supported values: {@code A4} (default), {@code A4_Landscape}.</p>
 *
 * <h2>Output location</h2>
 * <p>Generated JRXML files are written to {@code StandardLocation.SOURCE_OUTPUT},
 * which corresponds to {@code target/generated-sources} in a standard Maven build.
 * After review and design work in Jaspersoft Studio, copy the finished template to
 * {@code src/main/resources}.</p>
 */
@SupportedAnnotationTypes({
        "com.chaykin.jasper.core.annotation.JasperModularReport",
        "com.chaykin.jasper.core.annotation.JasperSubreport"
})
@SupportedOptions("jasper.template")
public class JrxmlGeneratorProcessor extends AbstractProcessor {

    private static final String JR_BEAN_COLLECTION_DS =
            "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource";

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    /**
     * Returns the latest supported Java source version so the processor does not emit
     * warnings when compiled with any future JDK.
     *
     * @return {@link SourceVersion#latestSupported()}
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Initializes the processor by capturing the compilation environment utilities.
     *
     * @param env the processing environment provided by the compiler
     */
    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.filer = env.getFiler();
        this.messager = env.getMessager();
        this.elementUtils = env.getElementUtils();
        this.typeUtils = env.getTypeUtils();
    }

    /**
     * Main processing entry point called by the compiler for each round.
     *
     * <p>Processes all classes annotated with {@link JasperModularReport} and
     * {@link JasperSubreport}. Returns early on the final round when no new sources
     * are being generated.</p>
     *
     * @param annotations the annotation types requested to be processed
     * @param roundEnv    the environment for the current processing round
     * @return {@code true} to claim the annotations; {@code false} on the final round
     */
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

    /**
     * Iterates over a set of annotated elements and triggers JRXML generation for each
     * class element found.
     *
     * @param elements the set of elements annotated with a jasper-modular annotation
     */
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

    /**
     * Generates or updates the JRXML template for the given annotated class.
     *
     * <p>Resolves the generation mode and template path from the annotation, collects
     * field descriptors, loads the appropriate template source (blank or existing),
     * and delegates writing to {@link JrxmlTemplateInjector}.</p>
     *
     * @param classElement the annotated report or subreport class
     * @throws Exception if template loading, injection, or writing fails
     */
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
                              + classElement.getSimpleName() + " -> " + templatePath);

        try (InputStream source = switch (mode) {
            case CREATE -> loadDefaultTemplate();
            case INJECT -> loadExistingOrDefault(templatePath);
            default -> throw new IllegalStateException("Unreachable: mode=" + mode);
        }) {
            writeOutput(templatePath, source, fields);
        }
    }

    /**
     * Creates the output JRXML resource and invokes the injector to write the result.
     *
     * @param templatePath the target classpath-relative path for the JRXML file
     * @param source       the input stream of the source template
     * @param fields       the list of field descriptors to inject
     * @throws Exception if file creation or injection fails
     */
    private void writeOutput(String templatePath,
                             InputStream source,
                             List<JrxmlParameter> fields) throws Exception {
        FileObject output = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                                                 "",
                                                 templatePath.replaceFirst("^/", ""));

        try (OutputStream out = output.openOutputStream()) {
            new JrxmlTemplateInjector(messager).inject(source, fields, out);
        }
    }

    /**
     * Collects field descriptors from the annotated class and its superclasses up to
     * (but not including) {@code JasperModularDataFiller}.
     *
     * <p>Fields annotated with {@link JasperIgnore} are excluded. Subreport fields,
     * collection fields, and scalar fields are handled by separate methods.</p>
     *
     * @param classElement the annotated class to inspect
     * @return a list of {@link JrxmlParameter} descriptors for all relevant fields
     */
    private List<JrxmlParameter> describeFields(TypeElement classElement) {
        List<JrxmlParameter> result = new ArrayList<>();
        TypeElement current = classElement;

        while (current != null && !isJasperModularDataFiller(current)) {
            ElementFilter.fieldsIn(current.getEnclosedElements())
                         .stream()
                         .filter(f -> f.getAnnotation(JasperIgnore.class) == null)
                         .forEach(f -> describeField(f, result));

            TypeMirror superclass = current.getSuperclass();
            current = superclass != null
                      ? (TypeElement) typeUtils.asElement(superclass)
                      : null;
        }
        return result;
    }

    /**
     * Describes a single field and appends the appropriate parameter descriptor(s)
     * to the result list.
     *
     * <p>Subreport fields produce two parameters ({@code <prefix>Report} and
     * {@code <prefix>MapParameter}). Collection fields produce a single
     * {@link JrxmlParameter} with an optional {@link JrxmlDataset}. All other fields
     * produce a scalar parameter.</p>
     *
     * @param field  the field to describe
     * @param result the list to append descriptors to
     */
    private void describeField(VariableElement field, List<JrxmlParameter> result) {
        TypeElement fieldClass = (TypeElement) typeUtils.asElement(field.asType());

        if (isSubreport(fieldClass)) {
            result.addAll(describeSubreportParameters(fieldClass));
            return;
        }

        if (isCollection(field.asType())) {
            describeCollectionField(field, result);
            return;
        }

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      field.asType().toString(),
                                      null));
    }

    /**
     * Returns {@code true} if the given class is annotated with {@link JasperSubreport}.
     *
     * @param fieldClass the class to check; may be {@code null}
     * @return {@code true} if the class is a subreport module
     */
    private boolean isSubreport(TypeElement fieldClass) {
        return fieldClass != null && fieldClass.getAnnotation(JasperSubreport.class) != null;
    }

    /**
     * Produces the two parameter descriptors required to wire a subreport into its
     * root report: {@code <prefix>Report} and {@code <prefix>MapParameter}.
     *
     * @param fieldClass the subreport class
     * @return a list containing exactly two {@link JrxmlParameter} descriptors
     */
    private List<JrxmlParameter> describeSubreportParameters(TypeElement fieldClass) {
        JasperSubreport ann = fieldClass.getAnnotation(JasperSubreport.class);
        String prefix = ann.prefix().isEmpty()
                        ? fieldClass.getSimpleName().toString()
                        : ann.prefix();
        return List.of(
                new JrxmlParameter(prefix + "Report",
                                   "net.sf.jasperreports.engine.JasperReport",
                                   null),
                new JrxmlParameter(prefix + "MapParameter",
                                   "java.util.Map",
                                   null)
        );
    }

    /**
     * Describes a collection field by resolving its element type and producing a
     * {@link JrxmlParameter} with an optional {@link JrxmlDataset}.
     *
     * <p>If the element type is a {@code JasperModularDataFiller} subclass (i.e. a
     * subreport module), a compile error is emitted - lists of subreports are not
     * supported. If the element type is a simple type (String, Number, etc.), no
     * dataset is generated.</p>
     *
     * @param field  the collection field
     * @param result the list to append the descriptor to
     */
    private void describeCollectionField(VariableElement field, List<JrxmlParameter> result) {
        TypeMirror elementType = resolveCollectionElementType(field.asType());
        if (elementType == null) {
            return;
        }

        TypeElement elementClass = (TypeElement) typeUtils.asElement(elementType);

        if (elementClass != null && isJasperModularDataFiller(elementClass)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                                  "List of subreports is not supported. Field: "
                                  + field.getSimpleName(), field);
            return;
        }

        JrxmlDataset dataset = elementClass != null && !isSimpleType(elementClass)
                               ? describeDataset(field.getSimpleName().toString(), elementClass)
                               : null;

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      JR_BEAN_COLLECTION_DS,
                                      dataset));
    }

    /**
     * Builds a {@link JrxmlDataset} by collecting all non-ignored declared fields
     * from the given element class.
     *
     * @param name         the dataset name (derived from the collection field name)
     * @param elementClass the class whose fields become dataset fields
     * @return a dataset descriptor with all relevant fields
     */
    private JrxmlDataset describeDataset(String name, TypeElement elementClass) {
        List<JrxmlDatasetField> fields =
                ElementFilter.fieldsIn(elementClass.getEnclosedElements())
                             .stream()
                             .filter(f -> f.getAnnotation(JasperIgnore.class) == null)
                             .map(f -> new JrxmlDatasetField(
                                     f.getSimpleName().toString(),
                                     f.asType().toString()))
                             .toList();

        return new JrxmlDataset(name, fields);
    }

    /**
     * Resolves the {@link GenerationMode} for the given annotated class.
     *
     * @param classElement the annotated class
     * @return the generation mode; defaults to {@link GenerationMode#INJECT}
     */
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

    /**
     * Resolves the JRXML template path for the given annotated class.
     *
     * <p>The path is taken from the annotation's {@code templatePath} attribute.
     * If neither annotation is present, a snake_case path under {@code reports/} is
     * generated from the class name.</p>
     *
     * @param classElement the annotated class
     * @return the classpath-relative JRXML path
     */
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

    /**
     * Loads the built-in blank JRXML template resource for the configured page size.
     *
     * @return an input stream for the blank template
     */
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

    /**
     * Attempts to load an existing JRXML resource from the class output directory.
     * Falls back to the blank template if the file is not found.
     *
     * @param templatePath the classpath-relative path to look up
     * @return an input stream for the existing or default template
     */
    private InputStream loadExistingOrDefault(String templatePath) {
        String path = templatePath.replaceFirst("^/", "");
        try {
            FileObject existing = filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
            InputStream stream = existing.openInputStream();
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injecting into existing: " + templatePath);
            return stream;
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Not found - creating from template: " + templatePath);
            return loadDefaultTemplate();
        }
    }

    /**
     * Resolves the blank template file name from the {@code jasper.template} processor
     * option. Defaults to {@code A4}.
     *
     * @return the template file name, e.g. {@code Blank_A4.jrxml}
     */
    private String resolveTemplateName() {
        return "Blank_"
               + processingEnv.getOptions().getOrDefault("jasper.template", "A4")
               + ".jrxml";
    }

    /**
     * Returns {@code true} if the given type is assignable to {@link java.util.Collection}.
     *
     * @param type the type mirror to check
     * @return {@code true} if the type is a collection
     */
    private boolean isCollection(TypeMirror type) {
        TypeElement collection = elementUtils.getTypeElement("java.util.Collection");
        return typeUtils.isAssignable(typeUtils.erasure(type),
                                      typeUtils.erasure(collection.asType()));
    }

    /**
     * Returns {@code true} if the given class is a well-known simple value type
     * (String, numeric wrappers, boolean, date/time types) for which no dataset
     * should be generated.
     *
     * @param element the class element to check
     * @return {@code true} if the class is a simple type
     */
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

    /**
     * Returns {@code true} if the given element represents
     * {@code com.chaykin.jasper.core.contract.JasperModularDataFiller}.
     *
     * @param element the element to check
     * @return {@code true} if the element is the base data filler class
     */
    private boolean isJasperModularDataFiller(TypeElement element) {
        return element.getQualifiedName()
                      .toString()
                      .equals("com.chaykin.jasper.core.contract.JasperModularDataFiller");
    }

    /**
     * Resolves the element type of a generic collection type, unwrapping wildcard
     * bounds if present.
     *
     * <p>For example, {@code List<RevenueItem>} returns {@code RevenueItem}, and
     * {@code List<? extends BaseItem>} returns {@code BaseItem}.</p>
     *
     * @param type the collection type mirror
     * @return the element type mirror, or {@code null} if not resolvable
     */
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
     * Converts a camelCase class name to snake_case for use as a default JRXML file name.
     *
     * @param name the camelCase name to convert
     * @return the snake_case equivalent in lowercase
     */
    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

}
