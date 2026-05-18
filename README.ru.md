# jasper-modular-library

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JasperReports](https://img.shields.io/badge/JasperReports-6.x%20%7C%207.x-red.svg)](https://community.jaspersoft.com/)

**Spring Boot библиотека для упрощения и унификации работы с JasperReports отчётами.**

Работа с JasperReports в Java-проектах — это сложно. Передача данных в шаблоны неудобна и хрупка,
подходы к этому разрозненные, а любой нетривиальный отчёт быстро превращается в хаос из SQL в XML,
ручного маппинга параметров и повторяющегося boilerplate.

Единственный реальный способ упростить эту работу — модульность. Когда отчёт собирается из
переиспользуемых компонентов-субрепортов, каждый со своими данными и дизайном, структура становится
понятной и управляемой. Но проблема в том, что работать с субрепортами в JasperReports напрямую —
ничуть не проще. Нет встроенного механизма, который делает это логичным: приходится вручную
объявлять параметры в JRXML корневого отчёта, синхронизировать их с Java-кодом, передавать каждый
параметр по имени — и при любом изменении обновлять всё в нескольких местах одновременно.

jasper-modular решает обе проблемы сразу — и хаос с данными, и сложность субрепортов. Вы просто
объявляете субрепорт как поле в Java-классе и аннотируете его — процессор сам генерирует нужные
параметры в JRXML при компиляции, рантайм сам передаёт всё что нужно. Данные описываются обычными
Java-объектами, а JRXML содержит только дизайн.

**Чем это отличается от всего остального**

В стандартном JasperReports данные в субрепорт обычно передаются поштучно: каждое поле,
необходимое субрепорту, нужно отдельно объявить в JRXML родительского отчёта и прокинуть вручную —
по одному `<subreportParameter>` на поле, по одному `params.put()` на поле в Java. При большом
количестве субрепортов это быстро превращается в десятки ручных записей в нескольких файлах.

jasper-modular использует другой приём: каждый субрепорт всегда получает ровно два параметра —
скомпилированный объект отчёта (`<prefix>Report`) и единую `Map<String, Object>`
(`<prefix>MapParameter`), содержащую все данные субрепорта. Внутри субрепорта мапа автоматически
распаковывается в отдельные параметры через встроенный механизм JasperReports
`REPORT_PARAMETERS_MAP`. Это малоизвестная возможность JasperReports, которая полностью устраняет
поштучный дриллинг параметров.

Оба параметра формируются автоматически из полей Java-класса во время компиляции: вы не объявляете
их, не прокидываете их, не думаете о них. К тому моменту как вы открываете шаблон в Jaspersoft
Studio, параметры уже на месте. Вы просто работаете со своими данными.

---

## Какую проблему решает

Работа с JasperReports в целом болезненна — передавать данные в шаблон и корректно их обрабатывать
сложно на каждом уровне. Модульность через субрепорты — лучший способ это упорядочить, но
стандартного механизма для удобной работы с ними нет. Каждый проект решает это по-своему, и почти
каждый подход порождает свои проблемы:

**Единый JSON на всех** — данные сериализуются в один гигантский JSON-объект, который передаётся во
все субрепорты через `JsonDataSource`. Субрепорты вытаскивают нужные данные через JSON-путь прямо в
JRXML. Логика выборки данных уходит в XML-шаблон, отлаживать это крайне тяжело.

**Прямое SQL-подключение** — субрепорт получает `REPORT_CONNECTION` (тот же JDBC что и корневой
отчёт) и сам делает SQL-запрос. Данные фильтруются через параметры передаваемые из корневого отчёта.
Бизнес-логика и SQL оседают в JRXML.

**Передача `REPORT_DATA_SOURCE` напрямую** — корневой датасорс пробрасывается в субрепорт.
Датасорс — consumable объект, используется один раз, после чего теряет данные. Порождает
трудноуловимые баги.

**Дриллинг параметров вручную через каскад субрепортов** — каждый параметр субрепорта объявляется
отдельно в JRXML корневого отчёта и маппится вручную. При большом количестве субрепортов это десятки
`<subreportParameter>` в шаблоне. При добавлении нового поля нужно обновить три места: Java-класс,
JRXML корневого отчёта, JRXML субрепорта — рассинхронизация и опечатки неизбежны.

**С jasper-modular:**

- Корневой отчёт — это просто Java-класс с аннотацией `@JasperModularReport`
- Субрепорт — это просто поле в Java-классе с аннотацией `@JasperSubreport`
- Аннотационный процессор сам генерирует все параметры и датасеты в JRXML при компиляции
- Рантайм сам компилирует, заполняет и собирает весь отчёт целиком — включая все субрепорты и их
  данные — никакого ручного boilerplate
- Все данные передаются через типизированные POJO-DTO — JRXML содержит только дизайн
- Построение отдельных компонентов отчёта и их переиспользование становится простым и естественным

---

## Требования

- Java 17+
- Spring Boot 3.3+ / 4.x
- JasperReports 6.x или 7.x

---

## Подключение

Добавьте стартер — он подтягивает всё необходимое кроме самого JasperReports, который вы указываете
сами:

```xml
<dependency>
    <groupId>io.github.hhdevr</groupId>
    <artifactId>jasper-modular-starter</artifactId>
    <version>2.0.0</version>
</dependency>

<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>${your.jasperreports.version}</version>
</dependency>
```

Добавьте аннотационный процессор в плагин компилятора (обязательно для генерации JRXML). Передайте
вашу версию JasperReports рядом — процессор использует её API во время компиляции:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.hhdevr</groupId>
                <artifactId>jasper-modular-processor</artifactId>
                <version>2.0.0</version>
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

Для экспорта в PDF добавьте расширение JasperReports (не включено в стартер намеренно):

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-pdf</artifactId>
    <version>${your.jasperreports.version}</version>
</dependency>
```

---

## Быстрый старт

### 1. Создайте модуль субрепорта

```java
@Getter
@Setter
@JasperSubreport(templatePath = "/reports/sub_items.jrxml", prefix = "Items")
public class ItemsModule extends SubreportModule {

    private List<LineItem> items;
    private BigDecimal subtotal;

    @Override
    public boolean isEmpty() { return items == null || items.isEmpty(); }
}
```

### 2. Создайте корневой отчёт

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

### 3. Соберите данные и отрендерьте отчёт

```java
ItemsModule items = new ItemsModule(lineItems, subtotal);

InvoiceReport report = new InvoiceReport();
report.setCustomerName("Acme Corp");
report.setInvoiceNumber("INV-001");
report.setTotal(BigDecimal.valueOf(1500.00));
report.setItemsModule(items);

JasperPrint print = new JasperModularRenderer<>().render(report);
```

### 4. Экспортируйте в PDF

```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
JRPdfExporter exporter = new JRPdfExporter();
exporter.setExporterInput(new SimpleExporterInput(print));
exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
exporter.exportReport();

byte[] pdf = out.toByteArray();
```

---

## Полный пример

Проект [jasper-modular-sample](https://github.com/hhdevr/jasper-modular-sample) демонстрирует
полноценный финансовый отчёт, построенный с помощью библиотеки. Отчёт собирается из вложенных
переиспользуемых модулей:

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

Каждый модуль — самостоятельный класс со своим JRXML-шаблоном. Корневой отчёт просто объявляет
их как поля — всё остальное происходит автоматически.

**Результат:**

<p>
  <img src="docs/sample_report_page-1.png" width="400" alt="Пример отчёта — страница 1"/>
  <img src="docs/sample_report_page-2.png" width="400" alt="Пример отчёта — страница 2"/>
</p>

[Скачать полный PDF](docs/financial_report.pdf)

---

## Философия работы с данными

Библиотека намеренно использует **POJO-DTO** как единственный способ передачи данных в отчёт.

Это означает что вы не пишете SQL-запросы внутри JRXML и не трансформируете данные в JSON. Вы
получаете данные из базы любым удобным способом (JPA, JDBC, внешний API), выполняете необходимые
вычисления и маппинг в обычном Java-коде, и передаёте готовые объекты в отчёт.

```java
// Получаете данные как обычно
List<RevenueItem> items = revenueRepository.findByPeriod(period);
double total = items.stream().mapToDouble(RevenueItem::getAmount).sum();
double growth = calculateGrowth(items);

// Собираете модуль — никакого SQL в шаблоне
RevenueModule revenue = new RevenueModule(total, growth, items);
```

Преимущества такого подхода:

- **Читаемость** — структура данных описана полями Java-класса, не SQL-запросом в XML
- **Контроль** — все вычисления, форматирование и бизнес-логика выполняются в Java до рендеринга
- **Типобезопасность** — компилятор и IDE помогают избежать опечаток в именах полей
- **Тестируемость** — модель отчёта — обычный POJO, легко тестируемый без рендеринга PDF

---

## Как это работает

### Во время компиляции

Аннотационный процессор (`JrxmlGeneratorProcessor`) запускается во время `mvn compile` и
инспектирует все классы, аннотированные `@JasperModularReport` и `@JasperSubreport`. Для каждого
класса он:

1. Читает все незаигнорированные поля через Java Compiler API
2. Определяет поля-субрепорты, поля-коллекции и скалярные поля
3. Вставляет недостающие элементы в существующий шаблон JRXML:
    - `<parameter>` для каждого поля
    - `<dataset>` и компонент `list` или `table` для каждого поля типа `Collection<T>`
    - Bands с субрепортами в секции `<detail>` для каждого поля-субрепорта
4. Записывает обновлённый JRXML в `target/generated-sources`

Существующие элементы определяются по имени и никогда не перезаписываются — пользовательский layout,
стили и выражения, созданные в Jaspersoft Studio, всегда сохраняются.

### В рантайме

При вызове `render(module)`:

1. Шаблон компилируется из JRXML-ресурса (или берётся из кэша)
2. Все поля обходятся через рефлексию и формируется `Map<String, Object>` параметров
3. Поля-субрепорты рекурсивно компилируются и заполняются, добавляя `<prefix>Report` и
   `<prefix>MapParameter`
4. Поля-коллекции оборачиваются в `JRBeanCollectionDataSource`
5. Вызывается `JasperFillManager.fillReport()` с картой параметров и пустым источником данных
6. Возвращается `JasperPrint` готовый для экспорта в любой формат

Циклические зависимости между субрепортами (например `A -> B -> A`) обнаруживаются автоматически —
выбрасывается `JasperModularException` с понятным сообщением о том где именно возник цикл, вместо
`StackOverflowError`.

### Прекомпиляция при старте

При запуске приложения `JasperReportPrecompiler` сканирует указанный пакет и прекомпилирует все
шаблоны, сохраняя результат в общий `JasperModularCompiler.CACHE`. Это исключает задержку компиляции
на первый запрос в продакшене.

Если какой-либо шаблон не компилируется — ошибка логируется и исключение пробрасывается дальше.
Приложение не запустится со сломанными шаблонами отчётов.

---

## Работа с шаблонами JRXML

### Новый отчёт — режим CREATE

Когда вы создаёте новый класс отчёта с `mode = GenerationMode.CREATE` и запускаете `mvn compile`, в
папке `target/generated-sources` появляется готовый JRXML-файл. Он уже содержит всё необходимое:

- `<parameter>` для каждого поля класса
- `<dataset>` с полями для каждой коллекции
- Компонент `list` или `table` для отображения данных коллекции
- Bands с субрепортами в секции `<detail>` для каждого поля-субрепорта

**Ваш workflow:**

1. Открываете сгенерированный файл из `target/generated-sources` в Jaspersoft Studio
2. Добавляете дизайн — размещаете элементы, настраиваете шрифты, цвета, заголовки
3. Сохраняете готовый шаблон в `src/main/resources/reports/`

Параметры, датасеты и субрепорты уже на месте — вам остаётся только дизайн.

### Существующий отчёт — режим INJECT (по умолчанию)

Когда вы добавляете новое поле или новый субрепорт в уже существующий класс отчёта, процессор при
следующей компиляции снова создаёт файл в `target/generated-sources`. Он содержит ваш оригинальный
шаблон плюс только недостающие элементы — новые параметры, датасеты, субрепорты. Всё что уже было в
шаблоне остаётся нетронутым.

**Ваш workflow:**

1. Добавили поле в Java-класс
2. Запустили `mvn compile`
3. Открыли обновлённый файл из `target/generated-sources` в Jaspersoft Studio — новые параметры уже
   есть
4. Разместили новые элементы в дизайне и вернули файл в `src/main/resources/reports/`

### Итоговая схема

```
                    mvn compile
                        |
        +---------------+---------------+
        |                               |
   новый класс                  существующий класс
   mode = CREATE                mode = INJECT (default)
        |                               |
        v                               v
  пустой шаблон               ваш шаблон + новые
  + все параметры             параметры/датасеты/
  + датасеты                  субрепорты
  + list/table-компоненты
  + суб-репорт bands
        |                               |
        +---------------+---------------+
                        v
              target/generated-sources/
                   your_report.jrxml
                        |
                        v
              Jaspersoft Studio — создай (CREATE) или дополни (INJECT) дизайн
                        |
                        v
              src/main/resources/reports/
                   your_report.jrxml  ← итоговый шаблон
```

---

## Режимы генерации

| Режим                   | Поведение                                                                   |
|-------------------------|-----------------------------------------------------------------------------|
| `INJECT` (по умолчанию) | Вставляет недостающие элементы в JRXML не трогая остальное                  |
| `CREATE`                | Создаёт новый JRXML из пустого шаблона, перезаписывая существующий файл     |
| `NONE`                  | Генерация не выполняется — управляйте JRXML полностью вручную               |

```java
@JasperModularReport(
        templatePath = "/reports/invoice.jrxml",
        mode = GenerationMode.CREATE
)
```

---

## Конфигурация

```yaml
jasper:
  modular:
    precompile-enabled: true           # по умолчанию: true
    base-package: com.example.reports  # обязательно для прекомпиляции
```

| Свойство                            | По умолчанию | Описание                               |
|-------------------------------------|--------------|----------------------------------------|
| `jasper.modular.precompile-enabled` | `true`       | Компилировать все шаблоны при старте   |
| `jasper.modular.base-package`       | `""`         | Пакет для сканирования классов отчётов |

---

## Справочник аннотаций

### `@JasperModularReport`

Помечает класс как корневой отчёт. Класс должен наследовать `ModularReport`.

| Атрибут        | Тип               | Обязательно | Описание                                       |
|----------------|-------------------|-------------|------------------------------------------------|
| `templatePath` | `String`          | Да          | Путь к JRXML-файлу в classpath                 |
| `mode`         | `GenerationMode`  | Нет         | Стратегия генерации (по умолчанию: `INJECT`)   |
| `orientation`  | `PageOrientation` | Нет         | Ориентация страницы (по умолчанию: `PORTRAIT`) |

### `@JasperSubreport`

Помечает класс как модуль субрепорта. Класс должен наследовать `SubreportModule`.

| Атрибут        | Тип               | Обязательно | Описание                                                       |
|----------------|-------------------|-------------|----------------------------------------------------------------|
| `templatePath` | `String`          | Да          | Путь к JRXML-файлу в classpath                                 |
| `prefix`       | `String`          | Нет         | Префикс для имён параметров (по умолчанию: простое имя класса) |
| `mode`         | `GenerationMode`  | Нет         | Стратегия генерации (по умолчанию: `INJECT`)                   |
| `orientation`  | `PageOrientation` | Нет         | Ориентация страницы (по умолчанию: `PORTRAIT`)                 |

### `@JasperCollection`

Управляет типом JRXML-компонента для поля-коллекции.

| Атрибут       | Тип                       | Обязательно | Описание                                               |
|---------------|---------------------------|-------------|--------------------------------------------------------|
| `type`        | `CollectionComponentType` | Нет         | `LIST` или `TABLE` (по умолчанию: `TABLE`)             |
| `columnWidth` | `int`                     | Нет         | Ширина каждой колонки в пикселях (по умолчанию: `100`) |

Если `@JasperCollection` отсутствует, процессор использует компонент `list` для обратной
совместимости.

```java
@JasperCollection(type = CollectionComponentType.TABLE, columnWidth = 80)
private List<LineItem> items;
```

### `@JasperIgnore`

Ставится на поле, чтобы исключить его из генерации JRXML и заполнения параметров.

```java
@JasperIgnore
private transient String internalState;
```

---

## Структура модулей

```
jasper-modular-parent
├── jasper-modular-core              — аннотации, контракты, базовые классы, рендерер
├── jasper-modular-autoconfigure     — Spring Boot автоконфигурация и прекомпилятор
├── jasper-modular-processor         — аннотационный процессор (JasperReports 6.x и 7.x)
└── jasper-modular-starter           — единственная зависимость для подключения
```

---

## Экспорт в другие форматы

`JasperModularRenderer.render()` возвращает формат-нейтральный `JasperPrint`. Добавьте нужный
экспортёр:

**XLSX:**

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-excel-poi</artifactId>
    <version>${your.jasperreports.version}</version>
</dependency>
```

Затем используйте `JRXlsxExporter` или другой экспортёр из JasperReports. HTML экспорт доступен из
основного `jasperreports` jar без дополнительных зависимостей.

---

## Лицензия

Apache License 2.0 — см. [LICENSE](LICENSE).

---

[![Analytics](https://static.scarf.sh/a.png?x-pxid=e35967d2-765b-40b5-bd0e-8849263b7b8f)](https://scarf.sh)
