package guiyom.golapi;

import guiyom.cellautomata.InputHelper;
import guiyom.cellautomata.Rule;
import lombok.Data;

import java.awt.image.BufferedImage;
import java.util.Random;

@Data
public class Job {

    private final String id = String.valueOf(Math.abs(new Random().nextLong()));
    private byte[] init;
    private int width;
    private int height;
    private int numRounds = 1;
    private Rule rule = Rule.Square2D.CONWAY.getRule();
    private boolean bound = true;
    private String output;
    private int delay;
    private int repeats;

    public void setInitFromImg(BufferedImage img) {
        setInit(InputHelper.fromImage(img));
        setWidth(img.getWidth());
        setHeight(img.getHeight());
    }
}
