# jasper-modular-library

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hhdevr/jasper-modular-starter)](https://mvnrepository.com/artifact/io.github.hhdevr/jasper-modular-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JasperReports](https://img.shields.io/badge/JasperReports-7.x-red.svg)](https://community.jaspersoft.com/)

**Spring Boot библиотека для упрощения и унификации работы с JasperReports отчётами.**

Репортинг в Java-проектах почти всегда превращается в ручную работу: SQL в JRXML, разрозненные
подходы к передаче данных, повторяющийся boilerplate для каждого нового отчёта. jasper-modular
выстраивает вокруг JasperReports небольшую экосистему соглашений и инструментов, которая делает
работу с отчётами предсказуемой и единообразной.

Ключ к этому упрощению - модульность через субрепорты. Правильно выстроенные субрепорты позволяют
переиспользовать куски данных и дизайна многократно, не плодя десятки несвязанных JRXML-шаблонов. Но
встраивание субрепортов - одна из самых болезненных задач в JasperReports: каждый решает её
по-своему, и почти всегда это заканчивается хрупким кодом. Отдельная проблема - удержание контекста:
какие параметры нужны каждому субрепорту, откуда они берутся, как их объявить в JRXML корневого
отчёта и правильно передать вниз - всё это нужно держать в голове и синхронизировать вручную между
Java-кодом и XML-файлами при каждом изменении.

jasper-modular решает это системно: вы просто объявляете субрепорт как поле в Java-классе и
аннотируете его - процессор сам генерирует нужные параметры в JRXML при компиляции, рантайм сам
передаёт всё что нужно.

**Чем это отличается от всего остального**

Каждый субрепорт в jasper-modular всегда порождает ровно два параметра в корневом JRXML —
скомпилированный объект отчёта и мапу с данными. Оба формируются автоматически из полей Java-класса
во время компиляции: вы не объявляете их, не прокидываете их, не думаете о них. К тому моменту как
вы открываете шаблон в Jaspersoft Studio, параметры уже на месте. Вы просто работаете со своими
данными.

---

## Какую проблему решает

Компоновка субрепортов в JasperReports - нетривиальная задача без стандартного решения. Разработчики
справляются с ней по-разному, и почти каждый подход несёт свои проблемы:

**Единый JSON на всех** - данные сериализуются в один гигантский JSON-объект, который передаётся во
все субрепорты через `JsonDataSource`. Субрепорты вытаскивают нужные данные через JSON-путь прямо в
JRXML. Логика выборки данных уходит в XML-шаблон, отлаживать это крайне тяжело.

**Прямое SQL-подключение** - субрепорт получает `REPORT_CONNECTION` (тот же JDBC что и корневой
отчёт) и сам делает SQL-запрос. Данные фильтруются через параметры передаваемые из корневого отчёта.
Бизнес-логика и SQL оседают в JRXML.

**Передача `REPORT_DATA_SOURCE` напрямую** - корневой датасорс пробрасывается в субрепорт.
Датасорс - consumable объект, используется один раз, после чего теряет данные. Порождает
трудноуловимые баги.

**Дриллинг параметров вручную через каскад субрепортов** - каждый параметр субрепорта объявляется
отдельно в JRXML корневого отчёта и маппится вручную. При большом количестве субрепортов это десятки
`<subreportParameter>` в шаблоне. При добавлении нового поля нужно обновить три места: Java-класс,
JRXML корневого отчёта, JRXML субрепорта - рассинхронизация и опечатки неизбежны.

**С jasper-modular:**

- Субрепорт - это просто поле в Java-классе с аннотацией `@JasperSubreport`
- Аннотационный процессор сам генерирует все параметры и датасеты в JRXML при компиляции
- Рантайм сам компилирует, заполняет и передаёт субрепорты - никакого ручного boilerplate
- Все данные передаются через типизированные POJO-DTO - JRXML содержит только дизайн

---

## Требования

- Java 17+
- Spring Boot 3.3+ / 4.x
- JasperReports 7.x

---

## Подключение

Единственная зависимость которая вам нужна - стартер. Он автоматически подтягивает все остальные
модули библиотеки. Добавьте его в `pom.xml`:

```xml
<dependency>
    <groupId>io.github.hhdevr</groupId>
    <artifactId>jasper-modular-starter</artifactId>
    <version>0.3.1</version>
</dependency>
```

Стартер на Maven
Repository: [io.github.hhdevr » jasper-modular-starter](https://mvnrepository.com/artifact/io.github.hhdevr/jasper-modular-starter)

Добавьте аннотационный процессор в плагин компилятора (обязательно для генерации JRXML):

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
            <!-- Необязательно: размер страницы для пустого шаблона (A4 или A4_Landscape) -->
            <arg>-Ajasper.template=A4</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

Для экспорта в PDF добавьте расширение JasperReports (не включено в стартер намеренно):

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-pdf</artifactId>
    <version>7.0.3</version>
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
    public Class<?> getRootReport() { return InvoiceReport.class; }

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
report.setCustomerName("ООО Ромашка");
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

// Собираете модуль - никакого SQL в шаблоне
RevenueModule revenue = new RevenueModule(total, growth, items);
```

Преимущества такого подхода:

- **Читаемость** - структура данных описана полями Java-класса, не SQL-запросом в XML
- **Контроль** - все вычисления, форматирование и бизнес-логика выполняются в Java до рендеринга
- **Типобезопасность** - компилятор и IDE помогают избежать опечаток в именах полей
- **Тестируемость** - модель отчёта - обычный POJO, легко тестируемый без рендеринга PDF

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
    - `<dataset>` и list-компонент для каждого поля типа `List<T>`
    - Bands с субрепортами в секции `<detail>` для каждого поля-субрепорта
4. Записывает обновлённый JRXML в `target/generated-sources`

Существующие элементы определяются по имени и никогда не перезаписываются - пользовательский layout,
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

### Новый отчёт - режим CREATE

Когда вы создаёте новый класс отчёта с `mode = GenerationMode.CREATE` и запускаете `mvn compile`, в
папке `target/generated-sources` появляется готовый JRXML-файл. Он уже содержит всё необходимое:

- `<parameter>` для каждого поля класса
- `<dataset>` с полями для каждой коллекции
- Компонент `list` для отображения данных коллекции
- Bands с субрепортами в секции `<detail>` для каждого поля-субрепорта

**Ваш workflow:**

1. Открываете сгенерированный файл из `target/generated-sources` в Jaspersoft Studio
2. Добавляете дизайн - размещаете элементы, настраиваете шрифты, цвета, заголовки
3. Сохраняете готовый шаблон в `src/main/resources/reports/`

Параметры, датасеты и субрепорты уже на месте - вам остаётся только дизайн.

### Существующий отчёт - режим INJECT (по умолчанию)

Когда вы добавляете новое поле или новый субрепорт в уже существующий класс отчёта, процессор при
следующей компиляции снова создаёт файл в `target/generated-sources`. Он содержит ваш оригинальный
шаблон плюс только недостающие элементы - новые параметры, датасеты, субрепорты. Всё что уже было в
шаблоне остаётся нетронутым.

**Ваш workflow:**

1. Добавили поле в Java-класс
2. Запустили `mvn compile`
3. Открыли обновлённый файл из `target/generated-sources` в Jaspersoft Studio - новые параметры уже
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
  + list-компоненты
  + суб-репорт bands
        |                               |
        +---------------+---------------+
                        v
              target/generated-sources/
                   your_report.jrxml
                        |
                        v
              Jaspersoft Studio - создай (для CREATE) 
                        |         дополни (для INJECT) дизайн отчета
                        v
              src/main/resources/reports/
                   your_report.jrxml  <- итоговый шаблон
```

---

## Режимы генерации

| Режим                   | Поведение                                                                   |
|-------------------------|-----------------------------------------------------------------------------|
| `INJECT` (по умолчанию) | Вставляет недостающие элементы в JRXML не трогая остальное                  |
| `CREATE`                | Создаёт новый JRXML из встроенного шаблона, перезаписывая существующий файл |
| `NONE`                  | Генерация не выполняется - управляйте JRXML полностью вручную               |

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

| Атрибут        | Тип              | Обязательно | Описание                                     |
|----------------|------------------|-------------|----------------------------------------------|
| `templatePath` | `String`         | Да          | Путь к JRXML-файлу в classpath               |
| `mode`         | `GenerationMode` | Нет         | Стратегия генерации (по умолчанию: `INJECT`) |

### `@JasperSubreport`

Помечает класс как модуль субрепорта. Класс должен наследовать `SubreportModule`.

| Атрибут        | Тип              | Обязательно | Описание                                                       |
|----------------|------------------|-------------|----------------------------------------------------------------|
| `templatePath` | `String`         | Да          | Путь к JRXML-файлу в classpath                                 |
| `prefix`       | `String`         | Нет         | Префикс для имён параметров (по умолчанию: простое имя класса) |
| `mode`         | `GenerationMode` | Нет         | Стратегия генерации (по умолчанию: `INJECT`)                   |

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
├── jasper-modular-core          - аннотации, контракты, базовые классы, рендерер
├── jasper-modular-autoconfigure - Spring Boot автоконфигурация и прекомпилятор
├── jasper-modular-processor     - APT аннотационный процессор для генерации JRXML
└── jasper-modular-starter       - единственная зависимость для подключения
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
    <version>7.0.3</version>
</dependency>
```

Затем используйте `JRXlsxExporter` или другой экспортёр из JasperReports. HTML экспорт доступен из
основного `jasperreports` jar без дополнительных зависимостей.

---

## Лицензия

Apache License 2.0 - см. [LICENSE](LICENSE).

---

[![Analytics](https://static.scarf.sh/a.png?x-pxid=e35967d2-765b-40b5-bd0e-8849263b7b8f)](https://scarf.sh)
