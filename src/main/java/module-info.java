module golapi {

    requires spark.core;
    requires org.slf4j;
    requires guiyom.cellautomata;
    requires java.desktop;
    requires com.rabbitmq.client;
    requires lombok;
    requires com.google.gson;
    requires jackson.dataformat.msgpack;
    requires com.fasterxml.jackson.databind;
    requires b2.sdk.core;
    requires b2.sdk.httpclient;
    requires logback.classic;

    opens guiyom.golapi to com.google.gson;
}