# jasper-modular-library

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hhdevr/jasper-modular-starter)](https://mvnrepository.com/artifact/io.github.hhdevr/jasper-modular-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JasperReports](https://img.shields.io/badge/JasperReports-7.x-red.svg)](https://community.jaspersoft.com/)

**A Spring Boot library for simplifying and unifying JasperReports report development.**

Reporting in Java projects almost always turns into manual work: SQL inside JRXML, inconsistent
approaches to passing data, repetitive boilerplate for every new report. jasper-modular builds a
small ecosystem of conventions and tooling around JasperReports that makes report development
predictable and consistent.

The key to this simplification is modularity through subreports. Well-structured subreports let you
reuse chunks of data and design across multiple reports without proliferating dozens of unrelated
JRXML templates. But embedding subreports is one of the most painful tasks in JasperReports -
everyone solves it differently, and the result is almost always brittle code. A separate challenge
is context management: which parameters each subreport needs, where they come from, how to declare
them in the root report JRXML and correctly pass them down - all of this must be kept in sync
manually between Java code and XML files on every change.

jasper-modular solves this systematically: you simply declare a subreport as a field in a Java class
and annotate it - the processor generates the required parameters in the JRXML at compile time, and
the runtime passes everything that is needed automatically.

**What makes this different**

Every subreport in jasper-modular always produces exactly two parameters in the root JRXML — the
compiled report object and the data map. Both are generated automatically from your Java class
fields at compile time: you never declare them, you never wire them, you never think about them. By
the time you open the template in Jaspersoft Studio, the parameters are already there. You just use
your data.

---

## The problem this solves

Composing subreports in JasperReports is a non-trivial task with no standard solution. Developers
handle it in very different ways, and almost every approach carries its own set of problems:

**One giant JSON for everything** - data is serialized into a single massive JSON object and passed
to all subreports via `JsonDataSource`. Subreports extract the data they need using JSON paths
directly in JRXML. Data-selection logic ends up in XML templates, which makes debugging extremely
painful.

**Direct SQL connection** - the subreport receives `REPORT_CONNECTION` (the same JDBC connection as
the root report) and executes its own SQL query. Data is filtered through parameters passed down
from the root report. Business logic and SQL accumulate inside JRXML.

**Passing `REPORT_DATA_SOURCE` directly** - the root report's data source is forwarded to the
subreport. A data source is a consumable object - it can only be used once, after which it is
exhausted. This causes subtle, hard-to-trace bugs.

**Manual parameter drilling through a cascade of subreports** - each subreport parameter is declared
individually in the root report's JRXML and mapped by hand. With many subreports this means dozens
of `<subreportParameter>` entries per template. Adding a new field requires updating three places:
the Java class, the root JRXML, and the subreport JRXML - drift and typos are inevitable.

**With jasper-modular:**

- A subreport is just a field in a Java class annotated with `@JasperSubreport`
- The annotation processor generates all parameters and datasets in the JRXML at compile time
- The runtime compiles, fills, and wires subreports automatically - no manual boilerplate
- All data is passed through typed POJO-DTOs - JRXML contains only design

---

## Requirements

- Java 17+
- Spring Boot 3.3+ / 4.x
- JasperReports 7.x

---

## Installation

The only dependency you need is the starter — it pulls in everything else automatically. Add it to
your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.hhdevr</groupId>
    <artifactId>jasper-modular-starter</artifactId>
    <version>0.3.1</version>
</dependency>
```

The starter on Maven
Repository: [io.github.hhdevr » jasper-modular-starter](https://mvnrepository.com/artifact/io.github.hhdevr/jasper-modular-starter)

Add the annotation processor to the compiler plugin (required for JRXML generation):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.hhdevr</groupId>
                <artifactId>jasper-modular-processor</artifactId>
                <version>0.3.1</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <!-- Optional: page size for the blank template (A4 or A4_Landscape) -->
            <arg>-Ajasper.template=A4</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

For PDF export, add the JasperReports PDF extension (intentionally excluded from the starter):

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-pdf</artifactId>
    <version>7.0.3</version>
</dependency>
```

---

## Quick start

### 1. Create a subreport module

```java
@Getter
@Setter
@JasperSubreport(templatePath = "/reports/sub_items.jrxml", prefix = "Items")
public class ItemsModule extends SubreportModule {

    private List<LineItem> items;
    private BigDecimal subtotal;

    @Override
    public Class<?> getRootReport() { return InvoiceReport.class; }

    @Override
    public boolean isEmpty() { return items == null || items.isEmpty(); }
}
```

### 2. Create the root report

```java
@Getter
@Setter
@JasperModularReport(templatePath = "/reports/invoice.jrxml")
public class InvoiceReport extends ModularReport {

    private String customerName;
    private String invoiceNumber;
    private BigDecimal total;
    private ItemsModule itemsModule;
    
}
```

### 3. Build the report and render it

```java
ItemsModule items = new ItemsModule(lineItems, subtotal);

InvoiceReport report = new InvoiceReport();
report.setCustomerName("Acme Corp");
report.setInvoiceNumber("INV-001");
report.setTotal(BigDecimal.valueOf(1500.00));
report.setItemsModule(items);

JasperPrint print = new JasperModularRenderer<>().render(report);
```

### 4. Export to PDF

```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
JRPdfExporter exporter = new JRPdfExporter();
exporter.setExporterInput(new SimpleExporterInput(print));
exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
exporter.exportReport();

byte[] pdf = out.toByteArray();
```

---

## Data philosophy

The library intentionally uses **POJO-DTOs** as the only way to pass data into a report.

This means you do not write SQL queries inside JRXML and do not transform data into JSON. You
retrieve data from the database using any approach you prefer (JPA, JDBC, external API), perform all
necessary calculations and mapping in plain Java code, and pass the ready objects to the report.

```java
// Fetch data as usual
List<RevenueItem> items = revenueRepository.findByPeriod(period);
double total = items.stream().mapToDouble(RevenueItem::getAmount).sum();
double growth = calculateGrowth(items);

// Build the module - no SQL in the template
RevenueModule revenue = new RevenueModule(total, growth, items);
```

Benefits of this approach:

- **Readability** - the data structure is described by Java fields, not SQL in XML
- **Control** - all calculations, formatting, and business logic happen in Java before rendering
- **Type safety** - the compiler and IDE prevent typos in field names
- **Testability** - the report model is a plain POJO, easily testable without rendering a PDF

---

## How it works

### At compile time

The annotation processor (`JrxmlGeneratorProcessor`) runs during `mvn compile` and inspects all
classes annotated with `@JasperModularReport` and `@JasperSubreport`. For each class it:

1. Reads all non-ignored fields via the Java Compiler API
2. Identifies subreport fields, collection fields, and scalar fields
3. Injects missing elements into the existing JRXML template:
    - `<parameter>` for each field
    - `<dataset>` and a `list` component for each `List<T>` field
    - Subreport bands in the `<detail>` section for each subreport field
4. Writes the updated JRXML to `target/generated-sources`

Existing elements are detected by name and never overwritten - custom layout, styles, and
expressions created in Jaspersoft Studio are always preserved.

### At runtime

When `render(module)` is called:

1. The template is compiled from the JRXML resource (or retrieved from the in-memory cache)
2. All fields are traversed via reflection to build the `Map<String, Object>` parameters map
3. Subreport fields are recursively compiled and filled, injecting `<prefix>Report` and
   `<prefix>MapParameter`
4. Collection fields are wrapped in `JRBeanCollectionDataSource`
5. `JasperFillManager.fillReport()` is called with the parameters map and an empty data source
6. The resulting `JasperPrint` is returned for export to any format

Circular subreport dependencies (e.g. `A -> B -> A`) are detected automatically and throw a
`JasperModularException` with a clear message identifying the offending class, rather than
propagating a `StackOverflowError`.

### Startup precompilation

On application startup, `JasperReportPrecompiler` scans the configured base package and precompiles
all report templates, storing them in the shared `JasperModularCompiler.CACHE`. This eliminates
compilation latency on the first report request in production.

If any template fails to compile, the error is logged and the exception is rethrown — the
application will not start with broken report templates.

---

## Working with JRXML templates

### New report - CREATE mode

When you create a new report class with `mode = GenerationMode.CREATE` and run `mvn compile`, a
ready-to-use JRXML file appears in `target/generated-sources`. It already contains everything
needed:

- `<parameter>` for every field in the class
- `<dataset>` with fields for every collection
- A `list` component for displaying collection data
- Subreport bands in the `<detail>` section for every subreport field

**Your workflow:**

1. Open the generated file from `target/generated-sources` in Jaspersoft Studio
2. Add your design - place elements, configure fonts, colors, headers
3. Save the finished template to `src/main/resources/reports/`

All parameters, datasets, and subreports are already in place - you only need to add the design.

### Existing report - INJECT mode (default)

When you add a new field or a new subreport to an existing report class, the processor generates a
new file in `target/generated-sources` on the next compile. It contains your original template plus
only the missing elements - new parameters, datasets, subreports. Everything that was already in the
template is left untouched.

**Your workflow:**

1. Add a field to the Java class
2. Run `mvn compile`
3. Open the updated file from `target/generated-sources` in Jaspersoft Studio - the new parameters
   are there
4. Place the new elements in the design and copy the file back to `src/main/resources/reports/`

### Overall flow

```
                    mvn compile
                        |
        +---------------+---------------+
        |                               |
   new class                    existing class
   mode = CREATE                mode = INJECT (default)
        |                               |
        v                               v
  blank template               your template + new
  + all parameters             parameters/datasets/
  + datasets                   subreports
  + list components
  + subreport bands
        |                               |
        +---------------+---------------+
                        v
              target/generated-sources/
                   your_report.jrxml
                        |
                        v
              Jaspersoft Studio - add (for CREATE mode) 
                        |         update (for INJECT mode) your design
                        v
              src/main/resources/reports/
                   your_report.jrxml  <- final template
```

---

## Generation modes

| Mode               | Behavior                                                                            |
|--------------------|-------------------------------------------------------------------------------------|
| `INJECT` (default) | Injects missing elements into the existing JRXML without touching existing content  |
| `CREATE`           | Creates a new JRXML from the built-in blank template, overwriting any existing file |
| `NONE`             | No processing - manage the JRXML entirely by hand                                   |

```java
@JasperModularReport(
        templatePath = "/reports/invoice.jrxml",
        mode = GenerationMode.CREATE
)
```

---

## Configuration

```yaml
jasper:
  modular:
    precompile-enabled: true           # default: true
    base-package: com.example.reports  # required for precompilation
```

| Property                            | Default | Description                        |
|-------------------------------------|---------|------------------------------------|
| `jasper.modular.precompile-enabled` | `true`  | Compile all templates at startup   |
| `jasper.modular.base-package`       | `""`    | Package to scan for report classes |

---

## Annotations reference

### `@JasperModularReport`

Marks a class as a root report. The class must extend `ModularReport`.

| Attribute      | Type             | Required | Description                             |
|----------------|------------------|----------|-----------------------------------------|
| `templatePath` | `String`         | Yes      | Classpath path to the JRXML file        |
| `mode`         | `GenerationMode` | No       | Generation strategy (default: `INJECT`) |

### `@JasperSubreport`

Marks a class as a subreport module. The class must extend `SubreportModule`.

| Attribute      | Type             | Required | Description                                        |
|----------------|------------------|----------|----------------------------------------------------|
| `templatePath` | `String`         | Yes      | Classpath path to the JRXML file                   |
| `prefix`       | `String`         | No       | Parameter name prefix (default: simple class name) |
| `mode`         | `GenerationMode` | No       | Generation strategy (default: `INJECT`)            |

### `@JasperIgnore`

Place on any field to exclude it from JRXML generation and runtime filling.

```java
@JasperIgnore
private transient String internalState;
```

---

## Module structure

```
jasper-modular-parent
├── jasper-modular-core          - annotations, contracts, base classes, renderer
├── jasper-modular-autoconfigure - Spring Boot autoconfiguration and precompiler
├── jasper-modular-processor     - APT annotation processor for JRXML generation
└── jasper-modular-starter       - single dependency entry point
```

---

## Exporting to other formats

`JasperModularRenderer.render()` returns a format-neutral `JasperPrint`. Add the exporter for the
format you need:

**XLSX:**

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-excel-poi</artifactId>
    <version>7.0.3</version>
</dependency>
```

Then use `JRXlsxExporter` or any other exporter from JasperReports. HTML export is available from
the core `jasperreports` jar without any additional dependency.

---

## License

Apache License 2.0 - see [LICENSE](LICENSE).

---

[![Analytics](https://static.scarf.sh/a.png?x-pxid=e35967d2-765b-40b5-bd0e-8849263b7b8f)](https://scarf.sh)
