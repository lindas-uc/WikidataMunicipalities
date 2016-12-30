# Municipality Index and Wikidata


# Goals

Explore the potential for interlinking the historised municipality index of the Federal Statistical Office (FSO) and Wikidata as well as possibilities to improve Wikidata with data from the FSO municipality.


# Initial survey
- Do Swiss municipalities consistently have the FSO municipality code?


    SELECT DISTINCT ?s ?sLabel ?code WHERE {
      ?s wdt:P31 wd:Q70208 .
      MINUS {?s wdt:P31 wd:Q685309}
      MINUS {?s wdt:P771 ?code}
      SERVICE wikibase:label {
        bd:serviceParam wikibase:language "en" .
       }
    }

The above SPARQL query executed against the Wikidata SPARQL endpoint at https://query.wikidata.org/ returned 6 resources.
**Corrective measure**: we manually compared the resources with the municipality index, 5 of the resources were simply missing the code and we added it, one resource was wrongly marked as Swiss municipality, we removed the offending type statement.

- How many Swiss municipalties does the Wikidata dataset contain?

The following query returns all current municipalities, i.e. all resources that are instance of “Municipality of Switzerland” (Q70208) but not also an instance of “Former Municipality of Switzerland” (Q685309).

    SELECT DISTINCT ?s ?sLabel ?code WHERE {
      ?s wdt:P31 wd:Q70208 .
      MINUS {?s wdt:P31 wd:Q685309}
      SERVICE wikibase:label {
        bd:serviceParam wikibase:language "en" .
      }
    }

The query returns 2312 resources.

- How many current municipalities are there according to the FSO dataset?

In the FSO dataset any current of former municipality has at least one Municipality-Version (gont:MunicipalityVersion) associated to it. A municipality version is a current one if and only if it has no abolition event (gont:abolitionEvent); a municipality is a current one if it has a current municipality version. The FSO dataset also contains some territorial entities that are not actual municipalities (e.g. cantonal lake portions), that’s why we restrict the search to instances of gont:PoliticalMunicipality which represents proper municipalities.
The following SPARQL query returns all ids of current municipalities when run against the lindas SPARQL Enpoint:

    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX gont: <https://gont.ch/>
    PREFIX dct: <http://purl.org/dc/terms/>
    
    SELECT DISTINCT ?munid
    FROM <http://lindas-data.ch/resource/histgemeinde>
    WHERE {
      ?municipalityversion a gont:MunicipalityVersion ;
      gont:municipality ?municipality .
      MINUS { ?municipalityversion gont:abolitionEvent ?abolitionEvent . }
      ?municipality a gont:PoliticalMunicipality .
      ?municipality dct:identifier ?munid .
    }

The query returns 2287 resources.

# Running a query over both datasets

Thanks to the SPARQL query federation mechanism it should be possible to send a query to one endpoint that retrieves additional data from another endpoint. For instance we wanted to create a query that gets the IRI of the corresponding Wikidata resource for all currentmunicipalities in the FSO dataset. The following query should return pairs of the IRI in the BFS dataset and the IRI in the Wikidata dataset:

    PREFIX wdt: <http://www.wikidata.org/prop/direct/>
    PREFIX gont: <https://gont.ch/>
    PREFIX dct: <http://purl.org/dc/terms/>
    
    SELECT DISTINCT ?municipality ?wdr
    WHERE {
      ?municipalityversion a gont:MunicipalityVersion ;
      gont:municipality ?municipality .
      MINUS { ?municipalityversion gont:abolitionEvent ?abolitionEvent . }
      ?municipality a gont:PoliticalMunicipality .
      ?municipality dct:identifier ?munid .
      BIND (str(?munid) AS ?smunid)
      SERVICE <https://query.wikidata.org/bigdata/namespace/wdq/sparql> { 
        ?wdr wdt:P771 ?smunid.  
      }
    } 

As ikidata stores the municipality code as RDF 1.0 style plain literal while the FSO dataset models the code as xsd:integer the above query contains a conversion in the BIND-clause.
Unfortunately the above query resulty in a timeout from the Lindas SPARQL Endpoint. Because of this we copied the FSO dataset to a local instance of the Apache Fuseki triple store. Against this triple store the above query could be executed and returned 1755 results. In other word more than 500 FSO municipalities could not be matched to a Wikidata counterpart. Looking at unmatched resources we realized that all municipalities with an identifier smaller than 1000 were not macthed. The reason for that was quickly identified: in the Wikidata Dataset the municipality codes are padded with leading zeros to have a constant length of 4 digits. Unfortunately we were unable to create a query that can handle this problem. This is very unfortunate as from a working SELECT query we could have created a CONSTRUCT query that returns a graph with e.g. rdfs:seeAlso statements that can be added to the FSO dataset.
As a work-around we wrote a small java program to achieve what we wanted to achieve more elegantly with the federated query with iterative programmatically generated queries. The resulting java code is available on GitHub at https://github.com/lindas-uc/WikidataMunicipalities.

## Comparing the datasets

With our Java code we could identify the following 16 municipality codes that were missing in Wikidata:

- Municipality missing in wikidata 298
- Municipality missing in wikidata 2162
- Municipality missing in wikidata 2235
- Municipality missing in wikidata 4590
- Municipality missing in wikidata 4495
- Municipality missing in wikidata 756
- Municipality missing in wikidata 3341
- Municipality missing in wikidata 5609
- Municipality missing in wikidata 6644
- Municipality missing in wikidata 2220
- Municipality missing in wikidata 3427
- Municipality missing in wikidata 2338
- Municipality missing in wikidata 297
- Municipality missing in wikidata 4723
- Municipality missing in wikidata 3379
- Municipality missing in wikidata 296

The causes for this missing codes were diverse. In some case the code was simply misspelled in other cases the municipality had an older FSO code, no municipality was actually missing in Wikidata. We manually fixed the municipality codes with the result that every current (political) municipalty in the FSO dataset has a matching entry in Wikidata.

## Linking the dataset

It clearly is in the interest of the value of the FSO dataset to provide links from the municiplaities it describes to the corresponding Wikidata resources. The FSO dataset already links to DBpedia resources with owl:sameAs statements. This approach seems however to be problematic as the cross-temporal identity criteria seem to be narrower for the resources in the FSO dataset than in the DBpedia and Wikidata datasets. For example the current municipality of Illnau-Effretikon is a different resource with different municipality code (296) than its predecessor (174) before the municipality incorporated the former municipality of Kyburg. Only the earlier of the two resources has a owl:sameAs relation to the DBpedia resource for Illnau-Effretikon. While this seems clearly wrong, as the DBpedia resource also describes the post-merger municipality adding a owl:sameAs statement to the DBpedia resource also to the new incarnation of Illnau-Effertikon would entail (by OWL inference) that both FSO resources for Illnau-Effretikon in the FSO dataset are also owl:sameAs with each other, i.e. strictly the same. This is clearly not the intention of the dataset creator as if the two Illnau-Effretikon resources collapse we would have a municipality with two municipality codes.
To avoid these semantic implications and problems with the linking to Wikidata we opted to use rdfs:seeAlso to point from the current municipalities in the FSO to their Wikidata counterpart. We added 2287 statements to the FSO datasets, the rdfs:seeAlso properties are now visible on the linked data view of the data, e.g. on http://classifications.data.admin.ch/municipality/296.

## Adding the official names

While each Swiss municipality has exactly one official name it was often not clear in the Wikidata dataset what this name is. For example according to Wikidata the bilingual municipality of Biel/Bienne is called “Biel/Bienne” in German and “Bienne” in French, there is no way to decide from the data in Wikidata which label is the official one. Luckily the Wikidata ontology foresees a solution for this: the property P1448 points to the official name of a resource.
However only 5 municipalities had an official name in Wikidata as the following query revealed:

    SELECT DISTINCT ?m ?p ?ps
     WHERE { 
       ?m wdt:P771 ?p .
       ?m wdt:P1448 ?ps .
     }

Benefiting from he experience collected with the [Wikidata:WikiProject Cultural heritage ](https://www.wikidata.org/wiki/Wikidata:WikiProject_Cultural_heritage/Reports/Ingesting_Swiss_heritage_institutions#Add_new_statements_to_Wikidata)we decided to use the [QuickStatements](https://tools.wmflabs.org/wikidata-todo/quick_statements.php) tool to add missing official names. Unfortunately we found that we could only create statements when the literal object would have a specified language. The FSO dataset however does not specify a language for the official names. An option we considered was to assume that the official language of the municipality is the the language of the official name, however this approach is undetermined  for the bilingual and trilingual municipalities. Also, there seems to be no correct language attribution for Biel/Bienne which probably doesn’t qualifies as monolingual text (and as such would technically violate the range constraint of P1448). We opted to use the special “und”-language tag to define the language as undefined. So the statements added with the QuickStatements tool would look as follows:

    Q64873 P1448 und:"Illnau-Effretikon"

Interestingly at the very beginning of the processing the QuickStatements tool returned a couple of errors about exceeding the rate limit:

    ERROR (set_monolang) : You've exceeded your rate limit. Please wait some time and try again.

And one even more seriously sounding error-message:

    ERROR (set_monolang) : '''Warning:''' This action has been automatically identified as harmful. Unconstructive actions will be quickly reverted, and egregious or repeated unconstructive editing will result in your account or IP address being blocked. If you believe this action to be constructive, you may submit it again to confirm it. A brief description of the abuse rule which your action matched is: Possible vandalism by adding nonsense

Nevertheless, the vast majority of names could successfully be added so that now 2224 municipalities in Wikidata have an official name.
The following query shows the 107 Swiss municipalities that are still lacking the official name property:

    PREFIX wdt: <http://www.wikidata.org/prop/direct/>
    PREFIX p: <http://www.wikidata.org/prop/>
    PREFIX wd: <http://www.wikidata.org/entity/>
    
    SELECT DISTINCT ?m ?p ?ps
     WHERE { 
       ?m wdt:P771 ?p . 
       ?m wdt:P31 wd:Q70208 . 
       minus {?m wdt:P31 wd:Q685309 . }
       minus{?m wdt:P1448 ?ps .}
     }
# Conclusions

This exploration has shown that the Wikidata dataset can be enhanced using RDF data provided in Lindas by the FSO and that official data can be made more valuable by linking to the respective Wikidata resources. While federated queries are powerful we experienced difficulties because of slight differences in the format of literal values, we had to write a imple client in Java to do what we originally wanted to achieve with a federated query.

