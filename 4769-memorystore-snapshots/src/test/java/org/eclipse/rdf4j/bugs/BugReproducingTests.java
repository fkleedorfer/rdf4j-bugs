package org.eclipse.rdf4j.bugs;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class BugReproducingTests {

    @Test
    public void reproduceBug_4769() throws IOException {
        Repository repo  = new SailRepository(new MemoryStore());
        RepositoryConnection con = repo.getConnection();
        ValueFactory factory = con.getValueFactory();
        importData(con, "reproduceBug_4769/mms.ttl", factory.createIRI("https://mms.researchstudio.at/mms/MMSOntology"));
        importData(con, "reproduceBug_4769/mms-ifc.ttl", factory.createIRI("https://mms.researchstudio.at/mms#IfcDataGraph"));
        importData(con, "reproduceBug_4769/qudt-quantitykind.ttl", factory.createIRI("https://mms.researchstudio.at/mms/QudtDataGraph"));
        importData(con, "reproduceBug_4769/qudt-unit.ttl",factory.createIRI("https://mms.researchstudio.at/mms/QudtDataGraph"));
        String query = loadQuery();
        for(int i = 0; i < 100; i++) {
            clearDefaultGraph(con);
            importData(con, "reproduceBug_4769/background.ttl", null);
            con.commit();
            TupleQuery prearedQuery = con.prepareTupleQuery(query);
            try (TupleQueryResult result = prearedQuery.evaluate()) {
                long resultCount = result.stream().count();
                Assertions.assertEquals(61, resultCount);
                System.out.println(String.format("Test execution %d: works as expected!",i));
            }
        }

    }

    private void clearDefaultGraph(RepositoryConnection con) {
        System.out.println("clearing default graph");
        con.remove((Resource)null, null, null);
        con.commit();
    }

    private static void importData(RepositoryConnection con, String inputfile, IRI graphIri) throws IOException {
        System.out.println(String.format("loading data %s into %s ", inputfile, graphIri == null ? "default graph" : "graph " + graphIri));
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(inputfile);
        con.add(in, RDFFormat.TURTLE, graphIri);
    }

    private static String loadQuery() throws IOException {
        InputStreamReader in = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        "reproduceBug_4769/query.rq"));
        BufferedReader reader = new BufferedReader(in);

        StringBuilder fileContent = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null){
            fileContent.append(line).append("\n");
        }
        return fileContent.toString();
    }
}
