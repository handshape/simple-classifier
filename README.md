# simple-classifier

A simple microservice that uses Lucene to classify inputs

## Build requirements

* A Java 11 JDK -- I recommend Azul Zulu, but any compliant JDK should work
* Apache Maven

To build, cd to the checkout directory and:
```
mvn clean install
```

## Usage

```
java -jar target/simple-classifier-service-1.0-SNAPSHOT-bin.jar [PORT] [FILE]
```

Where [PORT] is the port number on which you want the service to listen and 
[FILE] is the path to a Java .properties file in which your categories and 
classifers are defined.

For example:

```
shoes=loafers kicks slippers sandals
honorific=mr mrs sr dr mx
```

The category names are the keys, the values are the queries used to evaluate 
category membership.

The syntax for the query language can be found at 

https://lucene.apache.org/core/8_9_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description

Once the service is started, it will monitor the file for changes, and reload 
the definitions as necessary.

If you connect to your host and port with a web browser, you'll be presented 
with a simple UI for exercising the classifier. The UI will present one input 
control for each field referenced in the query definition file.

Classfiication jobs are sent to the service as GET requests with plain old URL
form-encoded parameters. The response comes in the form of a JSON body of the 
form:

```
{"categories":["category2","shoes"]}

```

where "category2" and "shoes" are the names of the categories to which the input matched.