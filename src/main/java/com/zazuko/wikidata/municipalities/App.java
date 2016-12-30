package com.zazuko.wikidata.municipalities;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.commons.rdf.Literal;
import org.apache.clerezza.commons.rdf.RDFTerm;
import org.apache.clerezza.rdf.core.LiteralFactory;


public class App {
    
    
    final static SparqlClient fsoSparqlClient = new SparqlClient("http://lindas-data.ch/sparql");
    final static SparqlClient wdSparqlClient = new SparqlClient("https://query.wikidata.org/bigdata/namespace/wdq/sparql");
    final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");


    
    public static void createFsoWikiLinks(Writer out) throws IOException, URISyntaxException {

        final String query = 
                "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n" +
                "PREFIX gont: <https://gont.ch/>\n" +
                "PREFIX dct: <http://purl.org/dc/terms/>\n" +
                "\n" +
                "SELECT DISTINCT ?municipality ?munid ?name \n" +
                "WHERE {\n" +
                "  ?municipalityversion a gont:MunicipalityVersion ;\n" +
                "  gont:municipality ?municipality ;\n" +
                "    gont:longName ?name.\n" +
                "  MINUS { ?municipalityversion gont:abolitionEvent ?abolitionEvent . }\n" +
                "  ?municipality a gont:PoliticalMunicipality .\n" +
                "  ?municipality dct:identifier ?munid .\n" +
                "} ";
        final List<Map<String, RDFTerm>> queryResults = fsoSparqlClient.queryResultSet(query);
        for (Map<String, RDFTerm> queryResult : queryResults) {
            final IRI municipality = (IRI)queryResult.get("municipality");
            final int municipalityCode = Integer.parseInt(
                    ((Literal)queryResult.get("munid")).getLexicalForm());
            final String name = ((Literal)queryResult.get("name")).getLexicalForm()
            final IRI wikiMuni = getWikiMuni(municipalityCode);
            /*System.out.println("<"+municipality.getUnicodeString()+"> rdfs:seeAlso <" 
                    + wikiMuni.getUnicodeString()+"> .");*/
            final String wikiId = wikiMuni.getUnicodeString().substring(wikiMuni.getUnicodeString().lastIndexOf('/')+1);
            out.write(wikiId+"\tP1448\tund:\""+name+"\" \n");
        }
        out.flush();
    }
    
    public static void main(String... args) throws Exception {
        FileWriter out = new FileWriter("out.txt");
        createFsoWikiLinks(out);
        out.close();
    }

    private static IRI getWikiMuni(int municipalityCode) throws IOException, URISyntaxException {
        final String fixedLengthMuniCode = String.format("%04d",municipalityCode);
        final String query = 
                "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n" +
                "PREFIX gont: <https://gont.ch/>\n" +
                "PREFIX dct: <http://purl.org/dc/terms/>\n" +
                "\n" +
                "SELECT DISTINCT ?wdr\n" +
                "WHERE {\n" +
                "  ?wdr wdt:P771 \""+fixedLengthMuniCode+"\".\n" +
                "} ";
        final List<Map<String, RDFTerm>> queryResults = wdSparqlClient.queryResultSet(query);
        if (queryResults.isEmpty()) {
            System.out.println("Municipality missing in wikidata "+municipalityCode);
            throw new RuntimeException("Municipality missing in wikidata "+municipalityCode);
        }
        if (queryResults.size() > 1) {
            System.out.println("More than one municipality in wikidata with code "+municipalityCode);
            throw new RuntimeException("More than one municipality in wikidata with code "+municipalityCode);
        }
        return (IRI) queryResults.iterator().next().get("wdr");
    }
    
}
