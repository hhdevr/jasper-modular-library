package com.chaykin.jasper.processor;

import com.chaykin.jasper.processor.model.JrxmlDataset;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class JrxmlTemplateInjector {

    private final Messager messager;

    public JrxmlTemplateInjector(Messager messager) {
        this.messager = messager;
    }

    public void inject(InputStream template,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws Exception {
        Document doc = parseXml(template);

        injectDatasets(doc, fields);
        injectParameters(doc, fields);
        injectSubreportBands(doc, fields);

        writeToStream(doc, output);
    }

    private void injectDatasets(Document doc, List<JrxmlParameter> fields) {
        Element root = doc.getDocumentElement();
        Node firstChild = root.getFirstChild();

        fields.stream()
              .filter(f -> f.dataset() != null)
              .filter(f -> !datasetExists(doc, f.dataset().name()))
              .map(f -> createDatasetElement(doc, f.dataset()))
              .forEach(el -> root.insertBefore(el, firstChild));
    }

    private Element createDatasetElement(Document doc, JrxmlDataset dataset) {
        Element el = doc.createElement("subDataset");

        for (JrxmlDatasetField field: dataset.fields()) {
            el.appendChild(createNamedElement(doc, "field", field.name(), field.jrxmlClass()));
        }
        el.setAttribute("name", dataset.name());
        return el;
    }

    private boolean datasetExists(Document doc, String name) {
        NodeList datasets = doc.getElementsByTagName("subDataset");
        for (int i = 0; i < datasets.getLength(); i++) {
            if (name.equals(((Element) datasets.item(i)).getAttribute("name"))) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Dataset already exists - skipping: " + name);
                return true;
            }
        }
        return false;
    }

    private void injectParameters(Document doc, List<JrxmlParameter> fields) {
        Node insertionPoint = findInsertionPoint(doc);

        for (JrxmlParameter field: fields) {
            if (parameterExists(doc, field.name())) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Parameter already exists - skipping: " + field.name());
                continue;
            }
            Element param = createNamedElement(doc, "parameter", field.name(), field.jrxmlClass());
            insertAfter(insertionPoint, param);
        }
    }

    private boolean parameterExists(Document doc, String name) {
        NodeList params = doc.getElementsByTagName("parameter");
        for (int i = 0; i < params.getLength(); i++) {
            if (name.equals(((Element) params.item(i)).getAttribute("name"))) {
                return true;
            }
        }
        return false;
    }

    private void injectSubreportBands(Document doc, List<JrxmlParameter> fields) {
        List<String> subreportPrefixes = fields.stream()
                                               .filter(f -> f.jrxmlClass().equals(
                                                       "net.sf.jasperreports.engine.JasperReport"))
                                               .map(f -> f.name().replace("Report", ""))
                                               .toList();

        if (subreportPrefixes.isEmpty()) {
            return;
        }

        Element detail = findOrCreateDetail(doc);

        for (String prefix: subreportPrefixes) {
            if (subreportBandExists(detail, prefix)) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Subreport band already exists - skipping: " + prefix);
                continue;
            }
            detail.appendChild(createSubreportBand(doc, prefix));
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injected subreport band: " + prefix);
        }
    }

    private Element findOrCreateDetail(Document doc) {
        NodeList details = doc.getElementsByTagName("detail");
        if (details.getLength() > 0) {
            return (Element) details.item(0);
        }
        Element detail = doc.createElement("detail");
        doc.getDocumentElement().appendChild(detail);
        return detail;
    }

    private boolean subreportBandExists(Element detail, String prefix) {
        NodeList bands = detail.getElementsByTagName("band");
        for (int i = 0; i < bands.getLength(); i++) {
            NodeList subreports = ((Element) bands.item(i))
                    .getElementsByTagName("element");
            for (int j = 0; j < subreports.getLength(); j++) {
                Element el = (Element) subreports.item(j);
                String expr = getTextContent(el, "expression");
                if (expr != null && expr.contains(prefix + "Report")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Element createSubreportBand(Document doc, String prefix) {
        Element band = doc.createElement("band");
        band.setAttribute("height", "100");

        Element subreport = doc.createElement("element");
        subreport.setAttribute("kind", "subreport");
        subreport.setAttribute("positionType", "Float");
        subreport.setAttribute("x", "0");
        subreport.setAttribute("y", "0");
        subreport.setAttribute("width", "555");
        subreport.setAttribute("height", "94");
        subreport.setAttribute("removeLineWhenBlank", "true");

        Element paramsExpr = doc.createElement("parametersMapExpression");
        paramsExpr.appendChild(doc.createCDATASection("$P{" + prefix + "MapParameter}"));
        subreport.appendChild(paramsExpr);

        Element dsExpr = doc.createElement("dataSourceExpression");
        dsExpr.appendChild(doc.createCDATASection("new JREmptyDataSource()"));
        subreport.appendChild(dsExpr);

        Element expr = doc.createElement("expression");
        expr.appendChild(doc.createCDATASection("$P{" + prefix + "Report}"));
        subreport.appendChild(expr);

        band.appendChild(subreport);
        return band;
    }

    private Element createNamedElement(Document doc,
                                       String tag,
                                       String name,
                                       String jrxmlClass) {
        Element el = doc.createElement(tag);
        el.setAttribute("class", jrxmlClass);
        el.setAttribute("name", name);
        return el;
    }

    private Node findInsertionPoint(Document doc) {
        NodeList params = doc.getElementsByTagName("parameter");
        if (params.getLength() > 0) {
            return params.item(params.getLength() - 1);
        }
        return doc.getDocumentElement().getFirstChild();
    }

    private void insertAfter(Node reference, Element newNode) {
        Node parent = reference.getParentNode();
        Node next = reference.getNextSibling();
        if (next != null) {
            parent.insertBefore(newNode, next);
        } else {
            parent.appendChild(newNode);
        }
    }

    private String getTextContent(Element el, String tagName) {
        NodeList nodes = el.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private Document parseXml(InputStream source) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(source);
        removeEmptyTextNodes(doc.getDocumentElement());
        return doc;
    }

    private void writeToStream(Document doc, OutputStream output) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(output));
    }

    private void removeEmptyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE
                && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else if (child.hasChildNodes()) {
                removeEmptyTextNodes(child);
            }
        }
    }

}
