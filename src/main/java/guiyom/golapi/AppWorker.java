package guiyom.golapi;

import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.rabbitmq.client.Delivery;
import guiyom.cellautomata.CellAutomata;
import guiyom.cellautomata.output.*;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;

public final class AppWorker {

    /**
     * Receives jobs and computes game of life rounds
     */
    void main() {

        try {
            Launcher.getAmqpChannel().basicConsume(Launcher.INPUT_QUEUE, false, this::handleDelivery, tag -> {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleDelivery(String tag, Delivery delivery) {

        Input in = new Input(delivery.getBody());
        Job job = Launcher.getKryo().readObject(in, Job.class);
        CellAutomata gol = new CellAutomata(job.getInit(), job.getWidth(), job.getHeight(), job.getRule(), job.isBound());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);

        AutomataOutput output;

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
            gol.record(output, job.getNumRounds(), true);

            if (output instanceof Closeable)
                ((Closeable) output).close();

            String fileName = "output/" + job.getId() + '.' + job.getOutput();
            B2ContentSource b2ContentSource = B2ByteArrayContentSource.builder(baos.toByteArray())
                                                      .setSha1OrNull(null)
                                                      .setSrcLastModifiedMillisOrNull(Instant.now().toEpochMilli())
                                                      .build();
            B2UploadFileRequest uploadRequest = B2UploadFileRequest.builder(
                    "golapi",
                    fileName,
                    getContentType(job.getOutput()),
                    b2ContentSource).build();

            B2FileVersion file = Launcher.getB2client().uploadSmallFile(uploadRequest);
            JobResult result = new JobResult();
            result.setId(job.getId());
            result.setResultUrl(new URL(Launcher.getB2client().getDownloadByIdUrl(file.getFileId())));
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
            Output out = new Output(baos2);
            Launcher.getKryo().writeObject(out, job);
            Launcher.getAmqpChannel().basicPublish("", Launcher.OUTPUT_QUEUE, null, baos2.toByteArray());
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
