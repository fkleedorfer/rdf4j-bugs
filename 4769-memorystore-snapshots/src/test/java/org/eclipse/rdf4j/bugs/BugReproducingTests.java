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
import org.eclipse.rdf4j.sail.memory.model.MemStatementList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class BugReproducingTests {

    @Test
    public void reproduceBug_4769()
                    throws IOException, NoSuchFieldException, IllegalAccessException, InterruptedException,
                    InvocationTargetException, NoSuchMethodException {
        MemoryStore store = new MemoryStore();
        Repository repo  = new SailRepository(store);
        RepositoryConnection con = repo.getConnection();
        ValueFactory factory = con.getValueFactory();
        importData(con, "reproduceBug_4769/mms.ttl", factory.createIRI("https://mms.researchstudio.at/mms/MMSOntology"));
        importData(con, "reproduceBug_4769/mms-ifc.ttl", factory.createIRI("https://mms.researchstudio.at/mms#IfcDataGraph"));
        importData(con, "reproduceBug_4769/qudt-quantitykind.ttl", factory.createIRI("https://mms.researchstudio.at/mms/QudtDataGraph"));
        importData(con, "reproduceBug_4769/qudt-unit.ttl",factory.createIRI("https://mms.researchstudio.at/mms/QudtDataGraph"));
        String query = loadQuery();
        TupleQuery prearedQuery = con.prepareTupleQuery(query);
        con.commit();
        for(int i = 0; i < 100; i++) {

            // Thread.sleep(1000); // does not fix the problem in this implementation, but changes the way it fails in an interesting way.

            System.out.println(String.format("========= Test execution %d ========",i));

            // here is the actual meat of the test, the query is further down
            clearDefaultGraph(con);
            con.commit();
            importData(con, "reproduceBug_4769/background.ttl", null);
            con.commit();

            // callCleanSnapshots(store); // does not fix the problem in this implementation...

            writeDiagnosticsToSysout(store, con);
            // here is the query that eventually fails (= returns 0 results)
            // retry many times in that case to see if the store comes back from the buggy state (it does not)
            long resultCount = 0;
            int retries = 0;
            while (resultCount == 0 && retries++ < 100) {
                try (TupleQueryResult result = prearedQuery.evaluate()) {
                    resultCount = result.stream().count();
                }
            }

            // fail if result is not 61 (write diagostics again when that happens)
            try {
                Assertions.assertEquals(61, resultCount);
            } catch (AssertionError e){
                System.out.println("TEST FAILED, writing diagnostic data again");
                writeDiagnosticsToSysout(store, con);
                throw e;
            }
            System.out.println(String.format("Test passed"));
        }

    }

    private void callCleanSnapshots(MemoryStore store)
                    throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object baseStore = getFieldValue(store, "store");
        callMethod(baseStore, "cleanSnapshots");
    }

    private void writeDiagnosticsToSysout(MemoryStore store, RepositoryConnection con) throws IllegalAccessException {
        Object baseStore = getFieldValue(store, "store");
        int snapshot = getFieldValue(baseStore, "currentSnapshot");
        int activeSnapshots = ((Map)getFieldValue(getFieldValue(baseStore, "snapshotMonitor"), "activeSnapshots")).size();
        MemStatementList  statements = getFieldValue(baseStore, "statements");
        System.out.println(String.format("Current snapshot: %d", snapshot));
        System.out.println(String.format("Active snapshots: %d",activeSnapshots));
        System.out.println(String.format("memStatementeList.size() = %d",statements.size()));
        System.out.println(String.format("memStatementeList.getGuaranteedLastIndexInUse() = %d",statements.getGuaranteedLastIndexInUse()));
        System.out.println(String.format("repo size: %d", con.size()));
    }

    private static <T> T getFieldValue(Object obj, String name) throws IllegalAccessException {
        Field fld = findField(obj.getClass(), name);
        if (fld == null){
            return null;
        }
        fld.setAccessible(true);
        return (T) fld.get(obj);
    }

    private static void callMethod(Object obj, String name)
                    throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Method m = obj.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(obj);
    }

    private static Field findField(Class<?> clazz, String name)  {
        try {
            Field fld = clazz.getDeclaredField(name);
            return fld;
        } catch (NoSuchFieldException e) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz != null){
                return findField(superClazz, name);
            }
        }
        return null;
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
