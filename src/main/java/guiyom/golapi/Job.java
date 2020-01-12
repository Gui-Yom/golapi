package guiyom.golapi;

import guiyom.cellautomata.InputHelper;
import lombok.Data;

import java.awt.image.BufferedImage;
import java.util.Random;

@Data
public class Job {

    private String id = String.valueOf(Math.abs(new Random().nextLong()));
    private byte[] init;
    private int width;
    private int height;
    private int numRounds = 1;
    private String rule = "CONWAY";
    private boolean bound = true;
    private String output = "gif";
    private int delay = 500;
    private int repeats = 0;

    public void setInitFromImg(BufferedImage img) {
        setInit(InputHelper.fromImage(img));
        setWidth(img.getWidth());
        setHeight(img.getHeight());
    }
}
