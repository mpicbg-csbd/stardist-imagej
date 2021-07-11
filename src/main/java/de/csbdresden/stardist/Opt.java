package de.csbdresden.stardist;

import java.util.LinkedHashMap;
import java.util.Map;

public class Opt {
    
    public static final String INPUT_IMAGE = "Input Image";
    public static final String PROB_IMAGE = "Probability/Score Image";
    public static final String DIST_IMAGE = "Distance Image";
    public static final String LABEL_IMAGE = "Label Image";
    
    public static final String MODEL = "Model";
    public static final String MODEL_FILE = "Model (.zip) from File";
    public static final String MODEL_URL = "Model (.zip) from URL";
    
    public static final String NORMALIZE_IMAGE = "Normalize Image";
    public static final String PERCENTILE_LOW = "Percentile low";
    public static final String PERCENTILE_HIGH = "Percentile high";
    
    public static final String PROB_THRESH = "Probability/Score Threshold";
    public static final String NMS_THRESH = "Overlap Threshold";
    
    public static final String OUTPUT_TYPE = "Output Type";
    public static final String OUTPUT_ROI_MANAGER = "ROI Manager";
    public static final String OUTPUT_LABEL_IMAGE = "Label Image";
    public static final String OUTPUT_BOTH = "Both";    
    public static final String OUTPUT_POLYGONS = "Polygons";
    
    public static final String NUM_TILES = "Number of Tiles";
    public static final String EXCLUDE_BNDRY = "Boundary Exclusion";
    public static final String ROI_POSITION = "ROI Position";
    public static final String ROI_POSITION_AUTO = "Automatic";
    public static final String ROI_POSITION_STACK = "Stack";
    public static final String ROI_POSITION_HYPERSTACK = "Hyperstack";
    public static final String VERBOSE = "Verbose";
    public static final String CSBDEEP_PROGRESS_WINDOW = "Show CNN Progress";
    public static final String SHOW_PROB_DIST = "Show CNN Output";
    public static final String SET_THRESHOLDS = "Set optimized postprocessing thresholds (for selected model)";    
    public static final String RESTORE_DEFAULTS = "Restore Defaults";
    public static final String PREVIEW = "Preview";
    
    // TODO: add descriptions for all options
    
    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<String, Object>();
    static {
        DEFAULTS.put(MODEL, StarDist2DModel.MODEL_DEFAULT);
        DEFAULTS.put(NORMALIZE_IMAGE, true);
        DEFAULTS.put(PERCENTILE_LOW, 1.0);
        DEFAULTS.put(PERCENTILE_HIGH, 99.8);
        DEFAULTS.put(PROB_THRESH, 0.5);
        DEFAULTS.put(NMS_THRESH, 0.4);
        DEFAULTS.put(OUTPUT_TYPE, OUTPUT_BOTH);
        DEFAULTS.put(NUM_TILES, 1);
        DEFAULTS.put(EXCLUDE_BNDRY, 2);
        DEFAULTS.put(ROI_POSITION, ROI_POSITION_AUTO);
        DEFAULTS.put(VERBOSE, false);
        DEFAULTS.put(CSBDEEP_PROGRESS_WINDOW, false);
        DEFAULTS.put(SHOW_PROB_DIST, false);
        DEFAULTS.put(PREVIEW, false);
    }
    
    static Object getDefault(final String key) {
        return DEFAULTS.get(key);
    }
    
    private Opt() {}

}
