package guiyom.golapi;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rabbitmq.client.Delivery;
import guiyom.cellautomata.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import static spark.Spark.*;

public final class App {

    /*
    POST /job -> Schedule a job, returns jobid
      query params or multipart data
    GET /job?id=<id> -> Get job state with jobid
      json result
    GET /job/result?id=<id> -> Get a job result (url) with jobid
      json result
    The message are passed through rabbitmq
    The worker stores the output to a B2 cloud storage
     */

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final Vector<String> jobs = new Vector<>();
    private final Map<String, JobResult> jobResults = new ConcurrentHashMap<>();

    App() {
        try {
            Launcher.getAmqpChannel().basicConsume(Launcher.OUTPUT_QUEUE, false, this::callbackJobFinished, tag -> {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Accept requests and schedule jobs
     */
    void main() {
        port(Integer.parseInt(System.getenv("PORT")));

        post("/job", "image/png, image/bmp", (q, a) -> {

            Job job = new Job();

            BufferedImage img = ImageIO.read(q.raw().getInputStream());
            job.setInitFromImg(img);
            // width and height params are ignored when passing an image

            final String rule = q.queryParams("rule");
            if (rule != null)
                job.setRule(Rule.Square2D.valueOf(rule).getRule());

            final String numRounds = q.queryParams("numRounds");
            if (numRounds != null)
                job.setNumRounds(Integer.parseInt(numRounds));

            final String bound = q.queryParams("bound");
            if (bound != null)
                job.setBound(Boolean.parseBoolean(bound));

            final String output = q.queryParams("output");
            if (bound != null)
                job.setOutput(output);

            final String delay = q.queryParams("delay");
            if (bound != null)
                job.setDelay(Integer.parseInt(delay));

            final String repeats = q.queryParams("repeats");
            if (bound != null)
                job.setRepeats(Integer.parseInt(repeats));

            log.info("Received a new job.");

            if (scheduleJob(job)) {
                a.status(200);
                a.type("text/plain");
                return job.getId();
            } else {
                a.status(500);
                a.type("text/plain");
                return "Failed to schedule job.";
            }
        });

        post("/job", "multipart/form-data", (q, a) -> {

            // TODO
            q.raw().getInputStream();
            log.info("Scheduled job : ");
            return null;
        });

        post("/job", "application/octet-stream", (q, a) -> {

            // TODO
            q.raw().getInputStream();
            log.info("Scheduled job : ");
            return null;
        });

        get("/result", (q, a) -> {

            String jobid = q.queryParams("id");
            if (jobid != null) {
                if (jobs.contains(jobid)) {
                    JobResult result = jobResults.get(jobid);
                    boolean finished = result != null;
                    a.status(200);
                    a.type("application/json");
                    JsonObject response = new JsonObject();
                    response.add("finished", new JsonPrimitive(finished));
                    response.add("result", Launcher.getGson().toJsonTree(result));
                    if (finished) {
                        jobs.remove(jobid);
                        jobResults.remove(result.getId());
                    }
                    return Launcher.getGson().toJson(response);
                } else {
                    a.status(404);
                    a.type("text/plain");
                    return "No job with id=" + jobid + " found.";
                }
            }
            a.status(400);
            a.type("text/plain");
            return "You must specify a job id with the query param 'id'.";
        });
    }

    private boolean scheduleJob(Job job) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
            Output out = new Output(baos);
            Launcher.getKryo().writeObject(out, job);
            Launcher.getAmqpChannel().basicPublish("", Launcher.INPUT_QUEUE, null, baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        log.info("Scheduled job : {}", job.getId());
        jobs.add(job.getId());
        return true;
    }

    private void callbackJobFinished(String tag, Delivery delivery) {
        Input in = new Input(delivery.getBody());
        JobResult result = Launcher.getKryo().readObject(in, JobResult.class);
        jobResults.put(result.getId(), result);
        log.info("Job {} has finished.", result.getId());

        try {
            Launcher.getAmqpChannel().basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
