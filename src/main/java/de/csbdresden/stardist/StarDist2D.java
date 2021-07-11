package de.csbdresden.stardist;

import static de.csbdresden.stardist.StarDist2DModel.MODELS;
import static de.csbdresden.stardist.StarDist2DModel.MODEL_DSB2018_HEAVY_AUGMENTATION;
import static de.csbdresden.stardist.StarDist2DModel.MODEL_DSB2018_PAPER;
import static de.csbdresden.stardist.StarDist2DModel.MODEL_HE_HEAVY_AUGMENTATION;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import ij.plugin.frame.RoiManager;
import org.scijava.Cancelable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.Previewable;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.csbdresden.CommandFromMacro;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

@Plugin(type = Command.class, label = "StarDist 2D", menu = {
        @Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
        @Menu(label = "StarDist"),
        @Menu(label = "StarDist 2D", weight = 1)
})
public class StarDist2D extends StarDist2DBase implements Command, Previewable, Cancelable {

    @Parameter(label="", visibility=ItemVisibility.MESSAGE, initializer="checkForCSBDeep")
    private final String msgTitle = "<html>" +
            "<table><tr valign='top'><td>" +
            "<h2>Object Detection with Star-convex Shapes</h2>" +
            "<a href='https://imagej.net/StarDist'>https://imagej.net/StarDist</a>" +
            "<br/><br/><small>Please cite our paper if StarDist was helpful for your research. Thanks!</small>" +
            "</td><td>&nbsp;&nbsp;<img src='"+getResource("images/logo.png")+"' width='100' height='100'></img><td>" +
            "</tr></table>" +
            "</html>";

    // ---------

    @Parameter(visibility=ItemVisibility.MESSAGE, label="<html><b>Neural Network Prediction</b></html>")
    private final String predMsg = "<html><hr width='100'></html>";

    @Parameter(label=Opt.INPUT_IMAGE) //, autoFill=false)
    private Dataset input;

    @Parameter(label=Opt.MODEL,
               choices={MODEL_DSB2018_HEAVY_AUGMENTATION,
                        MODEL_HE_HEAVY_AUGMENTATION,
                        MODEL_DSB2018_PAPER,
                        Opt.MODEL_FILE,
                        Opt.MODEL_URL}, style=ChoiceWidget.LIST_BOX_STYLE)
    private String modelChoice = (String) Opt.getDefault(Opt.MODEL);

    @Parameter(label=Opt.NORMALIZE_IMAGE)
    private boolean normalizeInput = (boolean) Opt.getDefault(Opt.NORMALIZE_IMAGE);

    @Parameter(label=Opt.PERCENTILE_LOW, stepSize="0.1", min="0", max="100", style=NumberWidget.SLIDER_STYLE, callback="percentileBottomChanged")
    private double percentileBottom = (double) Opt.getDefault(Opt.PERCENTILE_LOW);

    @Parameter(label=Opt.PERCENTILE_HIGH, stepSize="0.1", min="0", max="100", style=NumberWidget.SLIDER_STYLE, callback="percentileTopChanged")
    private double percentileTop = (double) Opt.getDefault(Opt.PERCENTILE_HIGH);

    @Parameter(label=Opt.PROB_IMAGE, type=ItemIO.OUTPUT)
    private Dataset prob;

    @Parameter(label=Opt.DIST_IMAGE, type=ItemIO.OUTPUT)
    private Dataset dist;

    // ---------

    @Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>NMS Postprocessing</b></html>")
    private final String nmsMsg = "<html><br/><hr width='100'></html>";

    @Parameter(label=Opt.LABEL_IMAGE, type=ItemIO.OUTPUT)
    private Dataset label;

    @Parameter(label=Opt.PROB_THRESH, stepSize="0.05", min="0", max="1", style=NumberWidget.SLIDER_STYLE)
    private double probThresh = (double) Opt.getDefault(Opt.PROB_THRESH);

    @Parameter(label=Opt.NMS_THRESH, stepSize="0.05", min="0", max="1", style=NumberWidget.SLIDER_STYLE)
    private double nmsThresh = (double) Opt.getDefault(Opt.NMS_THRESH);

    @Parameter(label=Opt.OUTPUT_TYPE, choices={Opt.OUTPUT_ROI_MANAGER, Opt.OUTPUT_LABEL_IMAGE, Opt.OUTPUT_BOTH}, style=ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
    private String outputType = (String) Opt.getDefault(Opt.OUTPUT_TYPE);

    // ---------

    @Parameter(visibility=ItemVisibility.MESSAGE, label="<html><br/><b>Advanced Options</b></html>")
    private final String advMsg = "<html><br/><hr width='100'></html>";

    @Parameter(label=Opt.MODEL_FILE, required=false)
    private File modelFile;

    @Parameter(label=Opt.MODEL_URL, required=false)
    protected String modelUrl;

    @Parameter(label=Opt.NUM_TILES, min="1", stepSize="1")
    private int nTiles = (int) Opt.getDefault(Opt.NUM_TILES);

    @Parameter(label=Opt.EXCLUDE_BNDRY, min="0", stepSize="1")
    private int excludeBoundary = (int) Opt.getDefault(Opt.EXCLUDE_BNDRY);
    
    @Parameter(label=Opt.ROI_POSITION, choices={Opt.ROI_POSITION_AUTO, Opt.ROI_POSITION_STACK, Opt.ROI_POSITION_HYPERSTACK}, style=ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
    private String roiPosition = (String) Opt.getDefault(Opt.ROI_POSITION);
    private String roiPositionActive = null;

    @Parameter(label=Opt.VERBOSE)
    private boolean verbose = (boolean) Opt.getDefault(Opt.VERBOSE);

    @Parameter(label=Opt.CSBDEEP_PROGRESS_WINDOW)
    private boolean showCsbdeepProgress = (boolean) Opt.getDefault(Opt.CSBDEEP_PROGRESS_WINDOW);

    @Parameter(label=Opt.SHOW_PROB_DIST)
    private boolean showProbAndDist = (boolean) Opt.getDefault(Opt.SHOW_PROB_DIST);

    // TODO: values for block multiple and overlap

    @Parameter(label=Opt.SET_THRESHOLDS, callback="setThresholds")
    private Button restoreThresholds;

    @Parameter(label=Opt.RESTORE_DEFAULTS, callback="restoreDefaults")
    private Button restoreDefaults;

    @Parameter(label=Opt.PREVIEW)
    private boolean preview = (boolean) Opt.getDefault(Opt.PREVIEW);

//    @Parameter(label=Opt.PREVIEW, callback="refreshROI")
//    private Button updateROI;

    // ---------

    private void restoreDefaults() {
        modelChoice = (String) Opt.getDefault(Opt.MODEL);
        normalizeInput = (boolean) Opt.getDefault(Opt.NORMALIZE_IMAGE);
        percentileBottom = (double) Opt.getDefault(Opt.PERCENTILE_LOW);
        percentileTop = (double) Opt.getDefault(Opt.PERCENTILE_HIGH);
        probThresh = (double) Opt.getDefault(Opt.PROB_THRESH);
        nmsThresh = (double) Opt.getDefault(Opt.NMS_THRESH);
        outputType = (String) Opt.getDefault(Opt.OUTPUT_TYPE);
        nTiles = (int) Opt.getDefault(Opt.NUM_TILES);
        excludeBoundary = (int) Opt.getDefault(Opt.EXCLUDE_BNDRY);
        roiPosition = (String) Opt.getDefault(Opt.ROI_POSITION);
        verbose = (boolean) Opt.getDefault(Opt.VERBOSE);
        showCsbdeepProgress = (boolean) Opt.getDefault(Opt.CSBDEEP_PROGRESS_WINDOW);
        showProbAndDist = (boolean) Opt.getDefault(Opt.SHOW_PROB_DIST);
        preview = (boolean) Opt.getDefault(Opt.PREVIEW);
    }

    private void percentileBottomChanged() {
        percentileTop = Math.max(percentileBottom, percentileTop);
    }

    private void percentileTopChanged() {
        percentileBottom = Math.min(percentileBottom, percentileTop);
    }

    private void setThresholds() {
        switch (modelChoice) {
        case Opt.MODEL_FILE:
        case Opt.MODEL_URL:
            showError("Only supported for built-in models.");
            break;
        default:
            final StarDist2DModel model = MODELS.get(modelChoice);
            probThresh = model.probThresh;
            nmsThresh = model.nmsThresh;
        }
    }

    private void checkForCSBDeep() {
        try {
            Class.forName("de.csbdresden.csbdeep.commands.GenericNetwork");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null,
                    "<html><p>"
                    + "StarDist relies on the CSBDeep plugin for neural network prediction.<br><br>"
                    + "Please install CSBDeep by enabling its update site.<br>"
                    + "Go to <code>Help > Update...</code>, then click on <code>Manage update sites</code>.<br>"
                    + "Please see <a href='https://github.com/csbdeep/csbdeep_website/wiki/CSBDeep-in-Fiji-%E2%80%93-Installation'>https://tinyurl.com/csbdeep-install</a> for more details."
                    + "</p><img src='"+getResource("images/csbdeep_updatesite.png")+"' width='498' height='324'>"
                    ,
                    "Required CSBDeep plugin missing",
                    JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("CSBDeep not installed");
        }
    }

    // ---------

    @Override
    public void run() {
        checkForCSBDeep();
        if (!checkInputs()) return;

        if (roiPosition.equals(Opt.ROI_POSITION_AUTO))
            roiPositionActive = input.numDimensions() > 3 && !input.isRGBMerged() ? Opt.ROI_POSITION_HYPERSTACK : Opt.ROI_POSITION_STACK;
        else
            roiPositionActive = roiPosition;

        File tmpModelFile = null;
        try {
            final HashMap<String, Object> paramsCNN = new HashMap<>();
            paramsCNN.put("input", input);
            paramsCNN.put("normalizeInput", normalizeInput);
            paramsCNN.put("percentileBottom", percentileBottom);
            paramsCNN.put("percentileTop", percentileTop);
            paramsCNN.put("clip", false);
            paramsCNN.put("nTiles", nTiles);
            paramsCNN.put("blockMultiple", 64);
            paramsCNN.put("overlap", 64);
            paramsCNN.put("batchSize", 1);
            paramsCNN.put("showProgressDialog", showCsbdeepProgress);

            switch (modelChoice) {
            case Opt.MODEL_FILE:
                paramsCNN.put("modelFile", modelFile);
                break;
            case Opt.MODEL_URL:
                paramsCNN.put("modelUrl", modelUrl);
                break;
            default:
                final StarDist2DModel pretrainedModel = MODELS.get(modelChoice);
                if (pretrainedModel.canGetFile()) {
                    final File file = pretrainedModel.getFile();
                    paramsCNN.put("modelFile", file);
                    if (pretrainedModel.isTempFile())
                        tmpModelFile = file;
                } else {
                    paramsCNN.put("modelUrl", pretrainedModel.url);
                }
                paramsCNN.put("blockMultiple", pretrainedModel.sizeDivBy);
                paramsCNN.put("overlap", pretrainedModel.tileOverlap);
            }

            final HashMap<String, Object> paramsNMS = new HashMap<>();
            paramsNMS.put("probThresh", probThresh);
            paramsNMS.put("nmsThresh", nmsThresh);
            paramsNMS.put("excludeBoundary", excludeBoundary);
            paramsNMS.put("roiPosition", roiPositionActive);
            paramsNMS.put("verbose", verbose);

            final LinkedHashSet<AxisType> inputAxes = Utils.orderedAxesSet(input);
            final boolean isTimelapse = inputAxes.contains(Axes.TIME);

            // TODO: option to normalize image/timelapse channel by channel or all channels jointly

            if (true && isTimelapse) {
                // TODO: option to normalize timelapse frame by frame (currently) or jointly
                final ImgPlus<? extends RealType<?>> inputImgPlus = input.getImgPlus();
                final long numFrames = input.getFrames();
                final int inputTimeDim = IntStream.range(0, inputAxes.size()).filter(d -> input.axis(d).type() == Axes.TIME).findFirst().getAsInt();
                for (int t = 0; t < numFrames; t++) {
                    final Dataset inputFrameDS = Utils.raiToDataset(dataset, "Input Frame",
                            Views.hyperSlice(inputImgPlus, inputTimeDim, t),
                            inputAxes.stream().filter(axis -> axis != Axes.TIME));
                    paramsCNN.put("input", inputFrameDS);
                    final Future<CommandModule> futureCNN = command.run(de.csbdresden.csbdeep.commands.GenericNetwork.class, false, paramsCNN);
                    final Dataset prediction = (Dataset) futureCNN.get().getOutput("output");

                    final Pair<Dataset, Dataset> probAndDist = splitPrediction(prediction);
                    final Dataset probDS = probAndDist.getA();
                    final Dataset distDS = probAndDist.getB();
                    paramsNMS.put("prob", probDS);
                    paramsNMS.put("dist", distDS);
                    paramsNMS.put("outputType", Opt.OUTPUT_POLYGONS);
                    if (showProbAndDist) {
                        // TODO: not implemented/supported
                        if (t==0) log.error(String.format("\"%s\" not implemented/supported for timelapse data.", Opt.SHOW_PROB_DIST));
                    }

                    final Future<CommandModule> futureNMS = command.run(StarDist2DNMS.class, false, paramsNMS);
                    final Candidates polygons = (Candidates) futureNMS.get().getOutput("polygons");
                    export(outputType, polygons, 1+t, numFrames, roiPositionActive);

                    status.showProgress(1+t, (int)numFrames);
                }
                label = labelImageToDataset(outputType);                
                // if (roiManager != null) OverlayCommands.listRois(roiManager.getRoisAsArray());

            } else {
                // note: the code below supports timelapse data too. differences to above:
                //       - joint normalization of all frames
                //       - requires more memory to store intermediate results (prob and dist) of all frames
                //       - allows showing prob and dist easily
                final Future<CommandModule> futureCNN = command.run(de.csbdresden.csbdeep.commands.GenericNetwork.class, false, paramsCNN);
                final Dataset prediction = (Dataset) futureCNN.get().getOutput("output");

                final Pair<Dataset, Dataset> probAndDist = splitPrediction(prediction);
                final Dataset probDS = probAndDist.getA();
                final Dataset distDS = probAndDist.getB();
                paramsNMS.put("prob", probDS);
                paramsNMS.put("dist", distDS);
                paramsNMS.put("outputType", outputType);
                if (showProbAndDist) {
                    prob = probDS;
                    dist = distDS;
                }

                final Future<CommandModule> futureNMS = command.run(StarDist2DNMS.class, false, paramsNMS);
                label = (Dataset) futureNMS.get().getOutput("label");
            }
            // call at the end of the run() method
            CommandFromMacro.record(this, this.command);
            
        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (tmpModelFile != null && tmpModelFile.exists())
                    tmpModelFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // this function is very cumbersome... is there a better way to do this?
    private Pair<Dataset, Dataset> splitPrediction(final Dataset prediction) {
        final RandomAccessibleInterval<FloatType> predictionRAI = (RandomAccessibleInterval<FloatType>) prediction.getImgPlus();
        final LinkedHashSet<AxisType> predAxes = Utils.orderedAxesSet(prediction);

        final int predChannelDim = IntStream.range(0, predAxes.size()).filter(d -> prediction.axis(d).type() == Axes.CHANNEL).findFirst().getAsInt();
        final long[] predStart = predAxes.stream().mapToLong(axis -> {
            return axis == Axes.CHANNEL ? 1 : 0;
        }).toArray();
        final long[] predSize = predAxes.stream().mapToLong(axis -> {
            return axis == Axes.CHANNEL ? prediction.dimension(axis)-1 : prediction.dimension(axis);
        }).toArray();

        final RandomAccessibleInterval<FloatType> probRAI = Views.hyperSlice(predictionRAI, predChannelDim, 0);
        final RandomAccessibleInterval<FloatType> distRAI = Views.offsetInterval(predictionRAI, predStart, predSize);

        final Dataset probDS = Utils.raiToDataset(dataset, Opt.PROB_IMAGE, probRAI, predAxes.stream().filter(axis -> axis != Axes.CHANNEL));
        final Dataset distDS = Utils.raiToDataset(dataset, Opt.DIST_IMAGE, distRAI, predAxes);

        return new ValuePair<>(probDS, distDS);
    }


    private boolean checkInputs() {
        final Set<AxisType> axes = Utils.orderedAxesSet(input);
        if (!( (input.numDimensions() == 2 && axes.containsAll(Arrays.asList(Axes.X, Axes.Y))) ||
               (input.numDimensions() == 3 && axes.containsAll(Arrays.asList(Axes.X, Axes.Y, Axes.TIME))) ||
               (input.numDimensions() == 3 && axes.containsAll(Arrays.asList(Axes.X, Axes.Y, Axes.CHANNEL))) ||
               (input.numDimensions() == 4 && axes.containsAll(Arrays.asList(Axes.X, Axes.Y, Axes.CHANNEL, Axes.TIME))) ))
            return showError("Input must be a 2D image or timelapse (with or without channels).");

        if (!( modelChoice.equals(Opt.MODEL_FILE) || modelChoice.equals(Opt.MODEL_URL) || MODELS.containsKey(modelChoice) ))
            return showError(String.format("Unsupported Model \"%s\".", modelChoice));

        if (!(roiPosition.equals(Opt.ROI_POSITION_AUTO) || roiPosition.equals(Opt.ROI_POSITION_STACK) || roiPosition.equals(Opt.ROI_POSITION_HYPERSTACK)))
            return showError(String.format("%s must be one of {\"%s\", \"%s\", \"%s\"}.", Opt.ROI_POSITION, Opt.ROI_POSITION_AUTO, Opt.ROI_POSITION_STACK, Opt.ROI_POSITION_HYPERSTACK));        

        return true;
    }


    @Override
    protected void exportPolygons(Candidates polygons) {}


    @Override
    protected ImagePlus createLabelImage() {
        return IJ.createImage(Opt.LABEL_IMAGE, "16-bit black", (int)input.getWidth(), (int)input.getHeight(), 1, 1, (int)input.getFrames());
    }


    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.launch(args);

        Dataset input = ij.scifio().datasetIO().open(StarDist2D.class.getClassLoader().getResource("yeast_crop.tif").getFile());
//        Dataset input = ij.scifio().datasetIO().open(StarDist2D.class.getClassLoader().getResource("yeast_timelapse.tif").getFile());
//        Dataset input = ij.scifio().datasetIO().open(StarDist2D.class.getClassLoader().getResource("patho_hyperstack.tif").getFile());
        ij.ui().show(input);
        
//        Recorder recorder = new Recorder();
//        recorder.show();

        final HashMap<String, Object> params = new HashMap<>();
        ij.command().run(StarDist2D.class, true, params);
    }

    private void refresh() {
        final HashMap<String, Object> params = new HashMap<>();

        params.put("input", input);
        params.put("modelChoice", modelChoice);
        params.put("normalizeInput", true);
        params.put("percentileBottom", percentileBottom);
        params.put("percentileTop", percentileTop);
        params.put("probThresh", probThresh);
        params.put("nmsThresh", nmsThresh);
        params.put("outputType", "ROI Manager");
        params.put("nTiles", 1);
        params.put("excludeBoundary", 2);
        params.put("roiPosition", Opt.ROI_POSITION_AUTO);

        command.run(StarDist2D.class, false, params);
    }

    private void refreshROI() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
        } else {
            refresh();
        }
    }

    @Override
    public void preview() {
        if (preview) {
            refreshROI();
        }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public void cancel(String s) {
        if (RoiManager.getInstance() == null) {
            new RoiManager().reset();
        } else {
            RoiManager.getInstance().reset();
        }
    }

    @Override
    public String getCancelReason() {
        return null;
    }
}
