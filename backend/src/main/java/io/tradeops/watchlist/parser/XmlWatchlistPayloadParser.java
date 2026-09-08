package io.tradeops.watchlist.parser;

import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.domain.WatchlistRecord;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class XmlWatchlistPayloadParser implements WatchlistPayloadParser {

    @Override
    public WatchlistPayload parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new WatchlistPayloadException("PAYLOAD_EMPTY", "The XML payload is empty.");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(rawPayload.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
            if (!"watchlist".equals(root.getTagName())) {
                throw new WatchlistPayloadException("XML_ROOT_INVALID", "The XML root must be watchlist.");
            }
            String provider = root.getAttribute("provider");
            String version = root.getAttribute("version");
            String dataOrigin = root.getAttribute("dataOrigin");
            List<WatchlistRecord> records = new ArrayList<>();
            NodeList nodes = root.getElementsByTagName("entity");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element entity = (Element) nodes.item(index);
                records.add(new WatchlistRecord(provider, entity.getAttribute("externalId"),
                        directText(entity, "entityName"), aliases(entity), entity.getAttribute("countryCode"),
                        entity.getAttribute("listingReason"), entity.getAttribute("status"), dataOrigin));
            }
            return new WatchlistPayload(provider, version, WatchlistPayload.TransportFormat.XML, records);
        } catch (WatchlistPayloadException exception) {
            throw exception;
        } catch (SAXException | java.io.IOException | javax.xml.parsers.ParserConfigurationException exception) {
            throw new WatchlistPayloadException("XML_PAYLOAD_INVALID", "The XML payload cannot be parsed safely.");
        }
    }

    private String directText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent();
            }
        }
        return "";
    }

    private List<String> aliases(Element entity) {
        List<String> aliases = new ArrayList<>();
        NodeList nodes = entity.getElementsByTagName("alias");
        for (int index = 0; index < nodes.getLength(); index++) {
            aliases.add(nodes.item(index).getTextContent());
        }
        return aliases;
    }
}