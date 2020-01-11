module golapi {

    requires spark.core;
    requires org.slf4j;
    requires guiyom.cellautomata;
    requires java.desktop;
    requires com.rabbitmq.client;
    requires lombok;
    requires com.google.gson;
    requires com.esotericsoftware.kryo;
    requires b2.sdk.core;
    requires b2.sdk.httpclient;

    opens guiyom.golapi to com.google.gson;
}