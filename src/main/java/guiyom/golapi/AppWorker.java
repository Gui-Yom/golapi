package guiyom.golapi;

import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import guiyom.cellautomata.CellAutomata;
import guiyom.cellautomata.Rule;
import guiyom.cellautomata.output.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;

public final class AppWorker {

    private static final Logger log = LoggerFactory.getLogger(AppWorker.class);

    /**
     * Receives jobs and computes game of life rounds
     */
    void main() {

        try {
            Launcher.getAmqpChannel().basicConsume(Launcher.INPUT_QUEUE, false, this::handleDelivery, tag -> {});

            log.info("Registered consumer -> putting main thread to sleep");

            // Sleep
            Object obj = new Object();
            synchronized (obj) {
                obj.wait();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleDelivery(String tag, Delivery delivery) {

        log.info("Received job !");

        ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

        Job job = null;
        try {
            job = mapper.readValue(delivery.getBody(), Job.class);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        log.info("Job id = {}", job.getId());

        CellAutomata gol = new CellAutomata(job.getInit(),
                job.getWidth(),
                job.getHeight(),
                Rule.Square2D.valueOf(job.getRule()).getRule(),
                job.isBound());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);

        AutomataOutput output;
        // This is resolved by switch expressions in java 12
        switch (job.getOutput()) {
            case "gif":
                output = new OutputGIF(baos, job.getDelay(), job.getRepeats());
                break;
            case "apng":
                output = new OutputAPNG(baos, job.getDelay(), job.getRepeats());
                break;
            case "png":
                output = new OutputPNG(baos);
                break;
            case "bmp":
                output = new OutputBMP(baos);
                break;

            default:
                return;
        }
        try {
            log.info("Started computing ...");
            gol.record(output, job.getNumRounds(), true);
            log.info("Finished computing !");

            if (output instanceof Closeable)
                ((Closeable) output).close();

            String fileName = "output/" + job.getId() + '.' + job.getOutput();
            B2ContentSource b2ContentSource = B2ByteArrayContentSource.builder(baos.toByteArray())
                                                      .setSha1OrNull(null)
                                                      .setSrcLastModifiedMillisOrNull(Instant.now().toEpochMilli())
                                                      .build();
            B2UploadFileRequest uploadRequest = B2UploadFileRequest.builder(
                    Launcher.B2_BUCKET_ID,
                    fileName,
                    getContentType(job.getOutput()),
                    b2ContentSource).build();

            B2FileVersion file = Launcher.getB2client().uploadSmallFile(uploadRequest);
            log.info("Uploaded resulting file !");
            JobResult result = new JobResult();
            result.setId(job.getId());
            result.setResultUrl(new URL(Launcher.getB2client().getDownloadByIdUrl(file.getFileId())));
            Launcher.getAmqpChannel().basicPublish("",
                    Launcher.OUTPUT_QUEUE,
                    null,
                    mapper.writeValueAsBytes(result));
            Launcher.getAmqpChannel().basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } catch (IOException | B2Exception e) {
            e.printStackTrace();
        }
    }

    String getContentType(String format) {
        switch (format) {
            case "gif":
                return "image/gif";
            case "apng":
                return "image/vnd.mozilla.apng";
            case "png":
                return "image/png";
            case "bmp":
                return "image/bmp";
            default:
                return "application/octet-stream";
        }
    }
}
