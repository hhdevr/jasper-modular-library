# jasper-modular-library

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JasperReports](https://img.shields.io/badge/JasperReports-6.x%20%7C%207.x-red.svg)](https://community.jaspersoft.com/)

**A Spring Boot library for simplifying and unifying JasperReports report development.**

jasper-modular brings modularity to JasperReports: you assemble a report from reusable subreport
components, each declared as an annotated field in a Java class. The processor generates the
required parameters in the JRXML at compile time, and the runtime passes everything automatically —
data is described as plain Java objects, and JRXML contains only design.

**What makes this different**

In standard JasperReports, subreport data is passed parameter-by-parameter: every field must be
declared individually in the parent JRXML and wired by hand — one `<subreportParameter>` per field,
one `params.put()` per field in Java. With many subreports this becomes dozens of manual entries
across multiple files.

jasper-modular uses a different technique: a single embedded subreport receives exactly two parameters —
the compiled report object (`<prefix>Report`) and a single `Map<String, Object>`
(`<prefix>MapParameter`) containing all of the subreport's data. Inside the subreport, the map
is automatically unpacked into individual parameters by JasperReports' built-in
`REPORT_PARAMETERS_MAP` mechanism — a little-known capability that eliminates
parameter-by-parameter drilling entirely. A `List` of subreport modules is rendered as a repeating
subreport instead — one instance per element (see [Lists of subreports](#lists-of-subreports)).

Both parameters are generated automatically from your Java class fields at compile time: you never
declare or wire them. By the time you open the template in Jaspersoft Studio, they are already
there.

---

## The problem this solves

Every project tames subreports differently, and almost every approach carries its own set of
problems:

**One giant JSON for everything** — data is serialized into a single massive JSON object passed to
all subreports via `JsonDataSource`, which extract what they need using JSON paths in JRXML; the
data-selection logic ends up in XML templates and is painful to debug.

**Direct SQL connection** — the subreport receives `REPORT_CONNECTION` and runs its own SQL query,
so business logic and SQL accumulate inside JRXML.

**Passing `REPORT_DATA_SOURCE` directly** — the root report's data source is forwarded to the
subreport, but a data source is consumable and can only be used once, causing subtle, hard-to-trace
bugs.

**Manual parameter drilling through a cascade of subreports** — each parameter is declared and
mapped by hand, so adding one field means updating three places (Java class, root JRXML, subreport
JRXML) and drift and typos are inevitable.

**With jasper-modular:**

- A root report is just a Java class annotated with `@JasperModularReport`
- A subreport is just a field in that class annotated with `@JasperSubreport`
- The annotation processor generates all parameters and datasets in the JRXML at compile time
- The runtime compiles, fills, and assembles the entire report — including all subreports and their
  data — no manual boilerplate
- All data is passed through typed POJO-DTOs — JRXML contains only design
- Build a component once and drop it into any report as a field

---

## Requirements

- Java 17+
- Spring Boot 3.3+ / 4.x
- JasperReports 6.x or 7.x

---

## Installation

Add the starter — it pulls in everything except JasperReports itself, which you provide:

```xml
<dependency>
    <groupId>io.github.hhdevr</groupId>
    <artifactId>jasper-modular-starter</artifactId>
    <version>2.0.1</version>
</dependency>

<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>${your.jasperreports.version}</version>
</dependency>
```

Add the annotation processor to the compiler plugin (required for JRXML generation). Pass your
JasperReports version alongside it so the processor can use the correct API at compile time:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.hhdevr</groupId>
                <artifactId>jasper-modular-processor</artifactId>
                <version>2.0.1</version>
            </path>
            <path>
                <groupId>net.sf.jasperreports</groupId>
                <artifactId>jasperreports</artifactId>
                <version>${your.jasperreports.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

For PDF export, add the JasperReports PDF extension (intentionally excluded from the starter):

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-pdf</artifactId>
    <version>${your.jasperreports.version}</version>
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
    public boolean isEmpty() {return items == null || items.isEmpty();}
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

JasperPrint print = new JasperModularRenderer().render(report);
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

## Full example

The [jasper-modular-sample](https://github.com/hhdevr/jasper-modular-sample) project demonstrates
a complete financial report built with the library. The report is assembled from nested reusable
modules:

```
CompanyReport (@JasperModularReport)
├── TitleSubModule (@JasperSubreport)
│   └── companyDetails, period, currency, totals
└── FinancialSubModule (@JasperSubreport)
    ├── RevenueSubModule (@JasperSubreport)
    │   └── totalRevenue, growthPercent, List<RevenueItem>
    ├── ExpenseSubModule (@JasperSubreport)
    │   └── totalExpenses, growthPercent, List<ExpenseItem>
    └── ProfitSubModule (@JasperSubreport)
        └── grossProfit, operatingProfit, netProfit, margin, List<ProfitBreakdown>
```

Each module is a standalone class with its own JRXML template. The root report declares them as
fields; the processor wires the parameters and the renderer assembles the document.

**Result:**

<p>
  <img src="docs/sample_report_page-1.png" width="400" alt="Sample report — page 1"/>
  <img src="docs/sample_report_page-2.png" width="400" alt="Sample report — page 2"/>
</p>

[Download full PDF](docs/financial_report.pdf)

---

## Data philosophy

The library intentionally uses **POJO-DTOs** as the only way to pass data into a report — no SQL in
JRXML, no JSON. You retrieve data however you prefer (JPA, JDBC, external API), perform all
calculations and mapping in plain Java code, and pass the ready objects to the report.

```java
// Fetch data as usual
List<RevenueItem> items = revenueRepository.findByPeriod(period);
double total = items.stream().mapToDouble(RevenueItem::getAmount).sum();
double growth = calculateGrowth(items);

// Build the module — no SQL in the template
RevenueModule revenue = new RevenueModule(total, growth, items);
```

The payoff: the data structure lives in Java fields instead of SQL buried in XML, and all
calculations and formatting run in plain code before rendering. The compiler catches typos in field
names, and the report model stays a POJO you can unit-test without ever producing a PDF.

---

## How it works

### At compile time

The annotation processor (`JrxmlGeneratorProcessor`) runs during `mvn compile`, inspects every class
annotated with `@JasperModularReport` and `@JasperSubreport`, and injects the missing elements into
the existing JRXML template:

- `<parameter>` for each field
- `<dataset>` and a `list` or `table` component for each `Collection<T>` field
- Subreport bands in the `<detail>` section for each subreport field

Existing elements are detected by name and never overwritten — custom layout, styles, and
expressions created in Jaspersoft Studio are always preserved.

### At runtime

When `render(module)` is called, the template is compiled from the JRXML resource (or taken from the
in-memory cache), and all fields are traversed via reflection to build the `Map<String, Object>`
parameters map. Subreport fields are recursively compiled and filled, injecting `<prefix>Report` and
`<prefix>MapParameter`. Collection fields are stored in the map as `JRBeanCollectionDataSource`
values. These are **parameters**, not the root data source: in JRXML you reference them via
`$P{fieldName}` in the `<dataSourceExpression>` of a `list` or `table` component. The fill uses
`JasperFillManager.fillReport()` with `JREmptyDataSource` as the root data source (the library never
uses band-iteration data sources) and returns a `JasperPrint` for export to any format.

Circular subreport dependencies (e.g. `A -> B -> A`) are detected automatically and throw a
`JasperModularException` identifying the offending class, rather than a `StackOverflowError`.

### Startup precompilation

On startup, `JasperReportPrecompiler` scans the configured base package and precompiles all
templates into the shared `JasperModularCompiler.CACHE`, eliminating compilation latency on the first
request. If any template fails to compile, the exception is rethrown — the application will not start
with broken report templates.

---

## Working with JRXML templates

### New report — CREATE mode

With `mode = GenerationMode.CREATE`, `mvn compile` produces a ready-to-use JRXML in
`target/generated-sources` containing everything — a `<parameter>` for every field, a `<dataset>`
and `list`/`table` component for every collection, and subreport bands for every subreport field.
Open it in Jaspersoft Studio, add your design (elements, fonts, colors, headers), and save the
finished template to `src/main/resources/reports/`.

### Existing report — INJECT mode (default)

When you add a field or subreport to an existing report class, the next `mvn compile` writes a file
to `target/generated-sources` containing your original template plus only the missing elements —
everything already in the template is left untouched. Open it in Jaspersoft Studio, place the new
elements in the design, and copy the file back to `src/main/resources/reports/`.

---

## Generation modes

| Mode               | Behavior                                                                           |
|--------------------|------------------------------------------------------------------------------------|
| `INJECT` (default) | Injects missing elements into the existing JRXML without touching existing content |
| `CREATE`           | Creates a new JRXML from a blank design, overwriting any existing file             |
| `NONE`             | No processing — manage the JRXML entirely by hand                                  |

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

| Attribute      | Type              | Required | Description                             |
|----------------|-------------------|----------|-----------------------------------------|
| `templatePath` | `String`          | Yes      | Classpath path to the JRXML file        |
| `mode`         | `GenerationMode`  | No       | Generation strategy (default: `INJECT`) |
| `orientation`  | `PageOrientation` | No       | Page orientation (default: `PORTRAIT`)  |

### `@JasperSubreport`

Marks a class as a subreport module. The class must extend `SubreportModule`.

| Attribute      | Type              | Required | Description                                        |
|----------------|-------------------|----------|----------------------------------------------------|
| `templatePath` | `String`          | Yes      | Classpath path to the JRXML file                   |
| `prefix`       | `String`          | No       | Parameter name prefix (default: simple class name) |
| `mode`         | `GenerationMode`  | No       | Generation strategy (default: `INJECT`)            |
| `orientation`  | `PageOrientation` | No       | Page orientation (default: `PORTRAIT`)             |

### `@JasperCollection`

Controls the JRXML component type for a collection field.

| Attribute     | Type                      | Required | Description                                 |
|---------------|---------------------------|----------|---------------------------------------------|
| `type`        | `CollectionComponentType` | No       | `LIST` or `TABLE` (default: `TABLE`)        |
| `columnWidth` | `int`                     | No       | Pixel width of each column (default: `100`) |

The default component type is `TABLE` — whether the annotation is present (without an explicit
`type`) or absent entirely. Use `type = CollectionComponentType.LIST` for a `list` component.

```java
@JasperCollection(type = CollectionComponentType.TABLE, columnWidth = 80)
private List<LineItem> items;
```

### Lists of subreports

How a `List` field is rendered depends entirely on its **element type** — the two cases are kept
strictly separate:

| Element type                | Rendered as                                                  | Field annotation         |
|-----------------------------|--------------------------------------------------------------|--------------------------|
| Plain data class (bean)     | An inline `list` / `table` component in the same template    | `@JasperCollection` (opt.) |
| A `@JasperSubreport` module | A **repeating subreport** — one subreport instance per element | none                     |

When the element type is a `@JasperSubreport` module, the list is treated as real subreports: the
processor injects a repeating subreport into the parent template, and at runtime each element is
filled recursively into its own parameter map and rendered once. No manual JRXML wiring is needed,
and `@JasperCollection` does not apply.

```java
// Inline — element is a plain bean → list/table of the report
@JasperCollection(type = CollectionComponentType.TABLE)
private List<LineItem> items;

// Repeating subreport — element is a @JasperSubreport module → rendered once per element
private List<DepartmentModule> departments;
```

**Choosing between them:**

- Inline `list` / `table` — for plain tabular rows. Lightweight: one template, no extra compilation.
- Repeating subreport — when each element is a self-contained, reusable section with its own layout
  (or its own nesting / page break).

A self-referential module (a `@JasperSubreport` class holding a `List` of itself) is rejected by the
circular-dependency guard.

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
├── jasper-modular-core              — annotations, contracts, base classes, renderer
├── jasper-modular-autoconfigure     — Spring Boot autoconfiguration and precompiler
├── jasper-modular-processor         — annotation processor (JasperReports 6.x and 7.x)
└── jasper-modular-starter           — single dependency entry point
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
    <version>${your.jasperreports.version}</version>
</dependency>
```

Then use `JRXlsxExporter` or any other exporter from JasperReports. HTML export is available from
the core `jasperreports` jar without any additional dependency.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

[![Analytics](https://static.scarf.sh/a.png?x-pxid=e35967d2-765b-40b5-bd0e-8849263b7b8f)](https://scarf.sh)
