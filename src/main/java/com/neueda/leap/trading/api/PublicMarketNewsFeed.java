package com.neueda.leap.trading.api;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.CRC32;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;

/** Real public headlines; no credentials, generated articles or invented publication dates. */
final class PublicMarketNewsFeed {
    static final String DEFAULT_URL = "https://feeds.finance.yahoo.com/rss/2.0/headline";
    private final RestClient client;
    private final String url;
    PublicMarketNewsFeed(String url) {
        this.url = url;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(4));
        client = RestClient.builder().requestFactory(factory).build();
    }
    List<Map<String,Object>> fetch(Map<String,String> params) {
        String symbol = params.getOrDefault("symbol", "^GSPC,^DJI,^IXIC");
        byte[] xml = client.get().uri(org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
            .queryParam("s",symbol).queryParam("region","US").queryParam("lang","en-US").build().encode().toUri())
            .header("User-Agent", "LeapTrader/1.0").exchange((request,response)-> {
                if(!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("News feed unavailable");
                byte[] bytes = response.getBody().readNBytes(1_048_577);
                if(bytes.length>1_048_576) throw new IllegalStateException("News feed exceeds limit");
                return bytes;
            });
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            if(!"rss".equals(document.getDocumentElement().getTagName())) throw new IllegalStateException("Invalid news feed");
            var items = document.getElementsByTagName("item");
            var result = new ArrayList<Map<String,Object>>();
            var links = new HashSet<String>();
            for(int i=0;i<items.getLength() && result.size()<100;i++) {
                var item=(Element)items.item(i);
                String headline=text(item,"title"),link=text(item,"link");
                if(headline.isBlank() || !link.startsWith("https://") || !links.add(link)) continue;
                Instant published;
                try { published=ZonedDateTime.parse(text(item,"pubDate"),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                catch(RuntimeException invalidDate) { continue; }
                if(params.containsKey("from") && (published.isBefore(LocalDate.parse(params.get("from")).atStartOfDay(ZoneOffset.UTC).toInstant())
                    || !published.isBefore(LocalDate.parse(params.get("to")).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))) continue;
                String summary=text(item,"description").replaceAll("<[^>]*>","").trim();
                var id=new CRC32();id.update(link.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                result.add(Map.of("id",id.getValue()+1,"headline",headline,"url",link,"datetime",published.getEpochSecond(),
                    "source","Yahoo Finance RSS","summary",summary.substring(0,Math.min(summary.length(),280)),
                    "related",params.getOrDefault("symbol",""),"image",""));
            }
            result.sort(Comparator.comparingLong(a->-((Number)a.get("datetime")).longValue()));
            return List.copyOf(result);
        } catch(Exception invalid) { throw new IllegalStateException("News feed could not be parsed",invalid); }
    }
    private String text(Element item,String name) {
        var nodes=item.getElementsByTagName(name);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
}
