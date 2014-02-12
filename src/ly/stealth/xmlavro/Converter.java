package ly.stealth.xmlavro;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;

public class Converter {
    private Datum.Type type;

    public Converter(Datum.Type type) {
        this.type = type;
    }

    public void convert(File inputFile, File outputFile) throws IOException, SAXException {
        try (InputStream inputStream = new FileInputStream(inputFile);
            OutputStream outputStream = new FileOutputStream(outputFile)) {
            convert(inputStream, outputStream);
        }
    }

    public void convert(InputStream inputStream, OutputStream outputStream) throws IOException, SAXException {
        Datum datum = convert(inputStream);
        org.apache.avro.Schema schema = datum.getType().toAvroSchema();

        DatumWriter<Object> datumWriter = new SpecificDatumWriter<>(schema);
        datumWriter.write(datum.toAvroDatum(), EncoderFactory.get().directBinaryEncoder(outputStream, null));
    }

    public <D extends Datum> D convert(String s) throws SAXException {
        try { return convert(new StringReader(s)); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    public <D extends Datum> D convert(Reader reader) throws IOException, SAXException { return convert(new InputSource(reader)); }

    public <D extends Datum> D convert(InputStream stream) throws IOException, SAXException { return convert(new InputSource(stream)); }

    public <D extends Datum> D convert(InputSource source) throws IOException, SAXException {
        try {
            DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = docBuilder.parse(source);
            return convert(doc.getDocumentElement());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public <D extends Datum> D convert(Element el) {
        return createDatum(type, el);
    }

    private <D extends Datum> D createDatum(Datum.Type type, Element el) {
        @SuppressWarnings("unchecked")
        D datum = (D) (type.isPrimitive() ? createValue((Value.Type) type, el.getTextContent()) : createRecord((Record.Type) type, el));
        return datum;
    }

    private Value createValue(Value.Type type, String text) {
        return new Value(type, parseValue(type, text));
    }

    private  Record createRecord(Record.Type type, Element el) {
        Record record = new Record(type);

        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element child = (Element) node;
            QName qName = new QName(child.getTagName(), child.getNamespaceURI());
            Record.Field field = type.getField(qName);

            Datum datum = createDatum(field.getType(), child);
            record.setDatum(field, datum);
        }

        NamedNodeMap attrMap = el.getAttributes();
        for (int i = 0; i < attrMap.getLength(); i++) {
            Attr attr = (Attr) attrMap.item(i);
            if (attr.getName().contains(":")) continue; // skip xmlns declarations

            QName qName = new QName(attr.getName(), attr.getNamespaceURI());
            Record.Field field = type.getField(qName, true);

            Value value = createValue((Value.Type) field.getType(), attr.getValue());
            record.setDatum(field, value);
        }

        return record;
    }

    private  Object parseValue(Value.Type type, String text) {
        if (type == Value.Type.BOOLEAN)
            return "true".equals(text) || "1".equals(text);

        if (type == Value.Type.INT)
            return Integer.parseInt(text);

        if (type == Value.Type.LONG)
            return Long.parseLong(text);

        if (type == Value.Type.FLOAT)
            return Float.parseFloat(text);

        if (type == Value.Type.DOUBLE)
            return Double.parseDouble(text);

        if (type == Value.Type.STRING)
            return text;

        throw new UnsupportedOperationException("Unsupported type " + type);
    }
}
