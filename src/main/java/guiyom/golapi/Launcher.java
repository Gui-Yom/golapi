package guiyom.golapi;

import ch.qos.logback.classic.Level;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

public final class Launcher {

    public static final String INPUT_QUEUE = "input";
    public static final String OUTPUT_QUEUE = "output";
    public static final String B2_BUCKET_ID = "9ccec72b1f395ddc6df10816";
    private static final Logger log = LoggerFactory.getLogger(Launcher.class);
    private static final String CLOUDAMQP_URL = System.getenv("CLOUDAMQP_URL");
    private static final String CLOUDAMQP_APIKEY = System.getenv("CLOUDAMQP_APIKEY");
    private static final String B2_APIKEY_ID = System.getenv("B2_APIKEY_ID");
    private static final String B2_APIKEY = System.getenv("B2_APIKEY");
    private static final Gson gson = new Gson();
    private static Channel amqpChannel;
    private static B2StorageClient b2client;

    public static B2StorageClient getB2client() {
        return b2client;
    }

    public static Gson getGson() {
        return gson;
    }

    public static Channel getAmqpChannel() {
        return amqpChannel;
    }

    public static void main(String[] args) {

        if (args != null && args.length > 0) {

            // Set log level to info
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ROOT")).setLevel(Level.INFO);

            final URI rabbitMqUrl;
            try {
                rabbitMqUrl = new URI(CLOUDAMQP_URL);
                log.info("RabbitMQ URL = {}", rabbitMqUrl);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUsername(rabbitMqUrl.getUserInfo().split(":")[0]);
            factory.setPassword(rabbitMqUrl.getUserInfo().split(":")[1]);
            factory.setHost(rabbitMqUrl.getHost());
            factory.setPort(rabbitMqUrl.getPort());
            factory.setVirtualHost(rabbitMqUrl.getPath().substring(1));
            try {
                amqpChannel = factory.newConnection().createChannel();
                amqpChannel.queueDeclare(INPUT_QUEUE, false, false, false, null);
                amqpChannel.queueDeclare(OUTPUT_QUEUE, false, false, false, null);
                amqpChannel.basicQos(1);
                amqpChannel.queuePurge(INPUT_QUEUE);
                amqpChannel.queuePurge(OUTPUT_QUEUE);
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
            log.info("Connected to AMPQ server !");

            b2client = B2StorageClientFactory.createDefaultFactory().create(B2_APIKEY_ID, B2_APIKEY, "golapi/1.0.0");
            log.info("Initialized connection to bucket storage !");

            if (args[0].equals("web"))
                new App().main();
            else
                new AppWorker().main();
        }
    }
}
