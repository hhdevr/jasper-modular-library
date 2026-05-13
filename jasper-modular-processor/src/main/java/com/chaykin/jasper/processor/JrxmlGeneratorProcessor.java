package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.CollectionComponentType;
import com.chaykin.jasper.core.annotation.GenerationMode;
import com.chaykin.jasper.core.annotation.JasperCollection;
import com.chaykin.jasper.core.annotation.JasperIgnore;
import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.annotation.PageOrientation;
import com.chaykin.jasper.processor.model.JrxmlDataset;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
 *   <li>{@link GenerationMode#CREATE} - generates a new JRXML from a blank programmatically
 *       created design, pre-populated with all parameters, datasets, list/table components and
 *       subreport bands derived from the class fields.</li>
 *   <li>{@link GenerationMode#INJECT} - reads the existing JRXML and injects only the
 *       elements that are not already present, preserving all user-defined layout.</li>
 *   <li>{@link GenerationMode#NONE} - skips generation entirely for this class.</li>
 * </ul>
 *
 * <h2>Collection component type</h2>
 * <p>Collection fields can be annotated with {@link JasperCollection} to control whether
 * a {@code list} or {@code table} component is generated, and to set the column width.
 * When the annotation is present, the default component type is {@code table}.
 * Without the annotation, a {@code list} component with
 * {@link JasperCollection#DEFAULT_COLUMN_WIDTH} is used for backwards compatibility.</p>
 * <pre>{@code
 * @JasperCollection(type = CollectionComponentType.TABLE, columnWidth = 100)
 * private List<LineItem> items;
 * }</pre>
 *
 * <h2>Page orientation</h2>
 * <p>The blank template orientation is controlled per class via
 * {@link JasperModularReport#orientation()} or {@link JasperSubreport#orientation()}.
 * No compiler options are needed.</p>
 * <pre>{@code
 * @JasperModularReport(templatePath = "/reports/wide.jrxml", orientation = PageOrientation.LANDSCAPE)
 * }</pre>
 *
 * <h2>Output location</h2>
 * <p>Generated JRXML files are written to {@code StandardLocation.SOURCE_OUTPUT},
 * which corresponds to {@code target/generated-sources} in a standard Maven build.</p>
 */
@SupportedAnnotationTypes({
        "com.chaykin.jasper.core.annotation.JasperModularReport",
        "com.chaykin.jasper.core.annotation.JasperSubreport"
})
public class JrxmlGeneratorProcessor extends AbstractProcessor {

    private static final String JR_BEAN_COLLECTION_DS =
            "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource";

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
                continue;
            }

            TypeElement classElement = (TypeElement) element;
            try {
                generate(classElement);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                                      "Failed to generate JRXML for "
                                      + classElement.getSimpleName() + ": " + e.getMessage()
                                      + "\n" + java.util.Arrays.toString(e.getStackTrace()),
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
                              + classElement.getSimpleName() + " -> " + templatePath);

        JasperDesign design = switch (mode) {
            case CREATE -> createEmptyDesign(classElement);
            case INJECT -> resolveDesign(templatePath, classElement);
            default -> throw new IllegalStateException("Unreachable: mode=" + mode);
        };

        writeOutput(templatePath, design, fields);
    }

    private void writeOutput(String templatePath,
                             JasperDesign design,
                             List<JrxmlParameter> fields) throws Exception {
        FileObject output = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                                                 "",
                                                 templatePath.replaceFirst("^/", ""));

        try (OutputStream out = output.openOutputStream()) {
            new JrxmlTemplateInjector(messager).inject(design, fields, out);
        }
    }

    /**
     * Loads an existing JRXML from the classpath, or falls back to creating a blank design.
     *
     * <p>Uses the processor classloader rather than {@code SOURCE_OUTPUT} because Maven
     * copies {@code src/main/resources} to {@code target/classes} before compilation,
     * making the existing template available on the classpath on subsequent runs.</p>
     */
    private JasperDesign resolveDesign(String templatePath,
                                       TypeElement classElement) throws Exception {
        String path = templatePath.replaceFirst("^/", "");
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream != null) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Injecting into existing: " + templatePath);
                return JRXmlLoader.load(stream);
            }
        }
        messager.printMessage(Diagnostic.Kind.NOTE,
                              "Not found - creating from template: " + templatePath);
        return createEmptyDesign(classElement);
    }

    private JasperDesign createEmptyDesign(TypeElement classElement) throws JRException {
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

    private boolean isSubreport(TypeElement fieldClass) {
        return fieldClass != null && fieldClass.getAnnotation(JasperSubreport.class) != null;
    }

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

        JasperCollection collectionAnn = field.getAnnotation(JasperCollection.class);
        CollectionComponentType componentType = collectionAnn != null
                                                ? collectionAnn.type()
                                                : CollectionComponentType.LIST;
        int columnWidth = collectionAnn != null
                          ? collectionAnn.columnWidth()
                          : JasperCollection.DEFAULT_COLUMN_WIDTH;

        JrxmlDataset dataset = elementClass != null && !isSimpleType(elementClass)
                               ? describeDataset(field.getSimpleName().toString(),
                                                 elementClass,
                                                 componentType,
                                                 columnWidth)
                               : null;

        result.add(new JrxmlParameter(field.getSimpleName().toString(),
                                      JR_BEAN_COLLECTION_DS,
                                      dataset));
    }

    private JrxmlDataset describeDataset(String name,
                                         TypeElement elementClass,
                                         CollectionComponentType componentType,
                                         int columnWidth) {
        List<JrxmlDatasetField> fields =
                ElementFilter.fieldsIn(elementClass.getEnclosedElements())
                             .stream()
                             .filter(f -> f.getAnnotation(JasperIgnore.class) == null)
                             .map(f -> new JrxmlDatasetField(
                                     f.getSimpleName().toString(),
                                     f.asType().toString()))
                             .toList();

        return new JrxmlDataset(name, fields, componentType, columnWidth);
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

    private PageOrientation resolveOrientation(TypeElement classElement) {
        JasperModularReport root = classElement.getAnnotation(JasperModularReport.class);
        if (root != null) {
            return root.orientation();
        }

        JasperSubreport sub = classElement.getAnnotation(JasperSubreport.class);
        if (sub != null) {
            return sub.orientation();
        }

        return PageOrientation.PORTRAIT;
    }

    private boolean isCollection(TypeMirror type) {
        TypeElement collection = elementUtils.getTypeElement("java.util.Collection");
        return typeUtils.isAssignable(typeUtils.erasure(type),
                                      typeUtils.erasure(collection.asType()));
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

    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
