# Test Migration Tool for DBS

## Setup

### Pre-requisites

* Java 8
* Maven 3.5
* ALM Octane workspace with an API key granting access to that workspace
* In order to benefit from parameters in the tests make sure that parameter references are surrounded by ``<>`` in 
the steps

### Setup

1. In ALM Octane add a new UDF called ``expected_result_udf`` of type String.  The label can be whatever you want
1. In the `pom.xml` file edit the following section according to your environment

    ```xml
    <configuration>
       <clientId>CLIENT_ID</clientId>
        <clientSecret>CLIENT_SECRET</clientSecret>
        <server>http(s)://server.com:8080</server>
        <sharedSpace>1001</sharedSpace>
        <workSpace>2002</workSpace>
    </configuration>
    ```

2.  Run the following maven command: ``mvn sdk-generate-entity-models:generate``
3. Export the excel file as a standard CSV and save as ``src/test/resources/dbs-test-case.csv``.  An example can be found
in that folder
4. Open the ``src/test/java/com/microfocus/adm/almoctane/migration/dbs/TestDBSMigration.java`` file and change the
parameters accordingly
5. Run the following maven command: ``mvn compile``

## Running

Running the tool can be done using the command ``mvn test`` but you may want to run it from your IDE