import ij.*;
import ij.io.*;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import ij.gui.WaitForUserDialog;
import ij.gui.GenericDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conf405_488 - Automated conf405/conf488 Image Analysis
 * 
 * This plugin automates the processing of image pairs (conf405 + conf488):
 * - Detects cells in conf405 images using thresholding and particle analysis
 * - Allows user review and manual adjustment of detected ROIs
 * - Measures both raw and thresholded conf488 intensities
 * - Saves results in organized folder structure
 * 
 * @author Gavin
 * @version 1.0
 */
public class Conf405_488 implements PlugIn {

    private static final int MIN_PARTICLE_SIZE = 100;
    private static final String THRESHOLD_METHOD = "Default dark no-reset";
    private volatile boolean processingCancelled = false;
    private Double manualThreshMin = null;
    private Double manualThreshMax = null;
    private Double manual488ThreshMin = null;
    private Double manual488ThreshMax = null;
    
    @Override
    public void run(String arg) {
        SwingUtilities.invokeLater(() -> createGUI());
    }

    // ================================
    // GUI Window
    // ================================
    private void createGUI() {
        JFrame frame = new JFrame("Conf405/488 Analysis Tool");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(350, 200);
        frame.setLayout(new BorderLayout(10, 10));

        // Main panel with padding
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Title label
        JLabel titleLabel = new JLabel("Automated Conf405/488 Analysis");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Description
        JLabel descLabel = new JLabel("<html><center>Process image pairs with cell detection<br>and intensity measurements</center></html>");
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        descLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        mainPanel.add(descLabel);

        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(2, 1, 10, 10));

        // -------- Button 1: Run Image Analysis --------
        JButton runMacroBtn = new JButton("Run Conf405/488 Analysis");
        runMacroBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        runMacroBtn.addActionListener(e -> {
            frame.toFront();
            // Run on a separate thread to avoid blocking the GUI
            new Thread(() -> {
                try {
                    processConfImages();
                } catch (Exception ex) {
                    IJ.log("ERROR in processing: " + ex.getMessage());
                    ex.printStackTrace();
                    IJ.showMessage("Error", "An error occurred during processing:\n" + ex.getMessage());
                }
            }).start();
        });

        // -------- Button 2: About/Help Button --------
        JButton helpBtn = new JButton("About / Help");
        helpBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        helpBtn.addActionListener(e -> showHelpDialog());

        buttonPanel.add(runMacroBtn);
        buttonPanel.add(helpBtn);

        mainPanel.add(buttonPanel);

        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ================================
    // Help/About Dialog
    // ================================
    private void showHelpDialog() {
        String helpText = "Conf405/488 Analysis Plugin\n\n" +
                "Expected File Format:\n" +
                "- conf405-1.tif, conf488-1.tif\n" +
                "- conf405-2.tif, conf488-2.tif\n" +
                "- etc.\n\n" +
                "Workflow:\n" +
                "1. Select folder containing image pairs\n" +
                "2. Plugin detects cells in conf405 images\n" +
                "3. Review and adjust ROIs for each image\n" +
                "4. Measurements saved automatically\n\n" +
                "Output:\n" +
                "- Results_all.csv (combined conf488 raw + threshold)\n" +
                "- ROIset.zip (saved ROIs)";
        
        IJ.showMessage("About Conf405/488 Plugin", helpText);
    }

    // ================================
    // Main Processing Function
    // ================================
    public void processConfImages() {
        try {
            processConfImagesInternal();
        } catch (Throwable t) {
            IJ.log("FATAL ERROR in processConfImages: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            IJ.showMessage("Fatal Error", "A fatal error occurred:\n" + t.getMessage());
        } finally {
            IJ.run("Close All");
        }
    }
    
    private void processConfImagesInternal() {
        // Reset cancellation flag
        processingCancelled = false;
        
        // Select directory
        String dir = IJ.getDirectory("Choose a Directory with your images");
        if (dir == null) {
            IJ.log("Processing cancelled - no directory selected");
            return;
        }

        // Create output directory
        String outDir = dir + "Results" + File.separator;
        File outDirFile = new File(outDir);
        if (!outDirFile.exists()) {
            outDirFile.mkdirs();
        }

        // Find all conf405 files
        File dirFile = new File(dir);
        String[] allFiles = dirFile.list();
        
        IJ.log("=== SCANNING DIRECTORY (v2) ===" );
        IJ.log("Directory path: " + dir);
        IJ.log("Total files in directory: " + (allFiles != null ? allFiles.length : 0));
        if (allFiles != null && allFiles.length > 0) {
            IJ.log("First few files:");
            for (int i = 0; i < Math.min(5, allFiles.length); i++) {
                IJ.log("  - " + allFiles[i]);
            }
        }
        
        IJ.log("Now filtering for conf405 files...");
        
        String[] list = dirFile.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lowerName = name.toLowerCase();
                boolean matches = (lowerName.startsWith("conf405") || lowerName.startsWith("conf 405")) && 
                       (lowerName.endsWith(".tif") || lowerName.endsWith(".tiff") || lowerName.endsWith(".stk"));
                if (matches) {
                    IJ.log("  Matched: " + name);
                }
                return matches;
            }
        });

        IJ.log("After filtering, found " + (list != null ? list.length : "null") + " conf405 files");

        if (list == null || list.length == 0) {
            IJ.showMessage("No Images Found", 
                "No conf405-*.tif files found in the selected directory.\n\n" +
                "Expected format: conf405-1.tif, conf405-2.tif, etc.");
            IJ.log("No conf405 files found - stopping.");
            return;
        }

        IJ.log("=== Starting batch processing ===");
        IJ.log("Directory: " + dir);
        IJ.log("Found " + list.length + " conf405 image(s)");
        IJ.log("Matching files:");

        ResultsTable combinedResults = new ResultsTable();

        int processedCount = 0;
        int skippedCount = 0;

        // Process each conf405 file
        for (String filename : list) {
            IJ.log("  -> " + filename);
            // Extract number from filename (e.g., "conf405-1.tif" -> "1")
            String numberPart = extractNumberFromFilename(filename);
            IJ.log("    Extracted number: " + numberPart);
            if (numberPart == null) {
                IJ.log("    WARNING: Could not extract number from " + filename);
                skippedCount++;
                continue;
            }

            String conf405Path = dir + filename;
            
            // Detect file extension from conf405 file
            String extension = "";
            if (filename.toLowerCase().endsWith(".tif")) extension = ".tif";
            else if (filename.toLowerCase().endsWith(".tiff")) extension = ".tiff";
            else if (filename.toLowerCase().endsWith(".stk")) extension = ".stk";
            
            // Try multiple conf488 naming patterns
            String conf488Path = null;
            String[] possibleNames = {
                "conf488-" + numberPart + extension,
                "conf 488-" + numberPart + extension,
                "conf488 -" + numberPart + extension
            };
            
            IJ.log("    Looking for conf488 with extension: " + extension);
            for (String possibleName : possibleNames) {
                String testPath = dir + possibleName;
                if (new File(testPath).exists()) {
                    conf488Path = testPath;
                    IJ.log("    Found matching conf488: " + possibleName);
                    break;
                }
            }
            
            if (conf488Path == null) {
                conf488Path = dir + "conf488-" + numberPart + extension;
            }

            // Check if corresponding conf488 exists
            if (!new File(conf488Path).exists()) {
                IJ.log("    SKIPPED: Missing conf488 for " + filename + " - tried: " + new File(conf488Path).getName());
                skippedCount++;
                continue;
            }

            // Process this image pair
            IJ.log("    Starting processing for pair " + numberPart);
            try {
                processImagePair(conf405Path, conf488Path, numberPart, outDir, combinedResults);
                processedCount++;
                IJ.log("    Successfully completed pair " + numberPart);
            } catch (Exception e) {
                if (processingCancelled) {
                    IJ.log("Processing stopped by user.");
                    break; // Exit the loop
                }
                IJ.log("    ERROR processing " + numberPart + ": " + e.getMessage());
                e.printStackTrace();
                skippedCount++;
            }
        }

        // Final summary
        IJ.log("=== Batch processing " + (processingCancelled ? "cancelled" : "complete") + "! ===");
        IJ.log("Processed: " + processedCount + " image pair(s)");
        if (skippedCount > 0) {
            IJ.log("Skipped: " + skippedCount + " image pair(s)");
        }
        IJ.log("Results saved in: " + outDir);

        if (combinedResults.size() > 0) {
            String combinedPath = outDir + "Results_all.csv";
            try {
                combinedResults.save(combinedPath);
                IJ.log("Combined results saved to: " + combinedPath);
            } catch (Exception e) {
                IJ.log("Failed to save combined results: " + e.getMessage());
            }
        }
        
        String statusMsg = processingCancelled ? "Processing Cancelled" : "Processing Complete";
        IJ.showMessage(statusMsg, 
            "Processed: " + processedCount + " image pair(s)\n" +
            (skippedCount > 0 ? "Skipped: " + skippedCount + " image pair(s)\n" : "") +
            (processingCancelled ? "Stopped by user request\n" : "") +
                "Results saved in:\n" + outDir);
    }

    // ================================
    // Process Single Image Pair
    // ================================
    private void processImagePair(String conf405Path, String conf488Path, 
                                  String identifier, String outDir, ResultsTable combinedResults) {
        
        IJ.log("\n--- Processing: " + identifier + " ---");

        // Create individual output folder
        String imageFolder = outDir + identifier + File.separator;
        File imageFolderFile = new File(imageFolder);
        if (!imageFolderFile.exists()) {
            imageFolderFile.mkdirs();
        }

        // Get or create ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager();
        }
        rm.reset();
        IJ.run("Clear Results");

        // ================================
        // Process conf405 Image
        // ================================
        IJ.log("Opening conf405 image...");
        ImagePlus imp405 = IJ.openImage(conf405Path);
        if (imp405 == null) {
            throw new RuntimeException("Failed to open conf405 image: " + conf405Path);
        }
        imp405.show();

        IJ.log("Detecting cells...");

        if (manualThreshMin == null || manualThreshMax == null) {
            IJ.log("Waiting for user to set threshold on conf405...");
            IJ.run(imp405, "Threshold...", "");
            WaitForUserDialog threshDialog = new WaitForUserDialog(
                    "Set Threshold",
                    "Adjust the threshold on the conf405 image, then click OK to use it for all images."
            );
            threshDialog.show();

            ImageProcessor ip = imp405.getProcessor();
            double minT = ip.getMinThreshold();
            double maxT = ip.getMaxThreshold();
            if (minT == ImageProcessor.NO_THRESHOLD || maxT == ImageProcessor.NO_THRESHOLD) {
                throw new RuntimeException("No threshold set. Please set a threshold and click OK.");
            }
            manualThreshMin = minT;
            manualThreshMax = maxT;
            IJ.log("Using manual threshold for run: min=" + manualThreshMin + ", max=" + manualThreshMax);
        }

        IJ.setThreshold(imp405, manualThreshMin, manualThreshMax);
        IJ.run(imp405, "Options...", "iterations=1 count=1 black do=nothing");
        IJ.run(imp405, "Convert to Mask", "");
        IJ.run(imp405, "Fill Holes", "");
        IJ.run(imp405, "Analyze Particles...", 
               "size=" + MIN_PARTICLE_SIZE + "-Infinity pixel display clear add");

        int roiCount = rm.getCount();
        IJ.log("Detected " + roiCount + " cell(s)");

        rm.runCommand("Show All");

        // ======== User Review Pause with Custom Dialog ========
        final boolean[] userChoice = new boolean[1]; // true = continue, false = cancel
        final boolean[] dialogClosed = new boolean[1]; // track if dialog is closed
        dialogClosed[0] = false;
        
        JDialog reviewDialog = new JDialog((Frame)null, "Cell Selection - " + identifier, false);
        reviewDialog.setLayout(new BorderLayout(10, 10));
        
        // Message panel
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        JLabel titleLabel = new JLabel("Review detected cells for: " + identifier);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 13));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(titleLabel);
        
        messagePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        JLabel detectedLabel = new JLabel("Detected: " + roiCount + " cell(s)");
        detectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(detectedLabel);
        
        messagePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        JLabel instructionsLabel = new JLabel("<html>You can:<br>" +
            "• Delete unwanted ROIs using ROI Manager<br>" +
            "• Add new ROIs using drawing tools<br>" +
            "• Reorder or modify ROIs as needed</html>");
        instructionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(instructionsLabel);
        
        reviewDialog.add(messagePanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton okButton = new JButton("OK - Continue");
        okButton.setPreferredSize(new Dimension(140, 30));
        okButton.addActionListener(e -> {
            userChoice[0] = true;
            dialogClosed[0] = true;
            reviewDialog.dispose();
        });
        
        JButton cancelButton = new JButton("Cancel - Stop");
        cancelButton.setPreferredSize(new Dimension(140, 30));
        cancelButton.setForeground(Color.RED);
        cancelButton.addActionListener(e -> {
            userChoice[0] = false;
            dialogClosed[0] = true;
            reviewDialog.dispose();
        });
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        reviewDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        reviewDialog.pack();
        reviewDialog.setLocationRelativeTo(null);
        reviewDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        reviewDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                userChoice[0] = false;
                dialogClosed[0] = true;
            }
        });
        
        reviewDialog.setVisible(true);
        
        // Wait for user to close dialog (non-blocking for image editing)
        while (!dialogClosed[0]) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                break;
            }
        }
        
        // Check if user cancelled
        if (!userChoice[0]) {
            IJ.log("Processing cancelled by user at image " + identifier);
            processingCancelled = true;
            imp405.changes = false; // Prevent save dialog
            imp405.close();
            rm.reset();
            IJ.run("Clear Results");
            throw new RuntimeException("Processing cancelled by user");
        }

        // Save ROI set
        int finalRoiCount = rm.getCount();
        if (finalRoiCount > 0) {
            rm.save(imageFolder + "ROIset.zip");
            IJ.log("Saved " + finalRoiCount + " ROI(s)");
        } else {
            IJ.log("Warning: No ROIs to save for " + identifier);
        }

        imp405.changes = false; // Prevent save dialog
        imp405.close();

        // ================================
        // Process conf488 Image
        // ================================
        if (finalRoiCount == 0) {
            IJ.log("Skipping conf488 measurements - no ROIs defined");
            return;
        }

        IJ.log("Opening conf488 image...");
        ImagePlus imp488 = IJ.openImage(conf488Path);
        if (imp488 == null) {
            throw new RuntimeException("Failed to open conf488 image: " + conf488Path);
        }
        imp488.show();

        // Set measurement options
        IJ.run("Set Measurements...", 
             "area mean integrated area_fraction raw redirect=None decimal=3");

        // ---- 1: Raw measurement ----
        IJ.log("Measuring raw conf488 intensities...");
        IJ.run("Clear Results");
        rm.runCommand(imp488, "Measure");
        ResultsTable rawRt = ResultsTable.getResultsTable();
        int rawCount = rawRt.size();
        double[] rawArea = getColumnValues(rawRt, new String[]{"Area"});
        double[] rawMean = getColumnValues(rawRt, new String[]{"Mean"});
        double[] rawIntDen = getColumnValues(rawRt, new String[]{"IntDen"});
        double[] rawAreaPct = getColumnValues(rawRt, new String[]{"%Area", "AreaFrac", "AreaFraction"});
        double[] rawRawIntDen = getColumnValues(rawRt, new String[]{"RawIntDen", "RawInt"});

        // ---- 2: Thresholded measurement ----
        IJ.log("Measuring thresholded conf488 intensities...");
        if (manual488ThreshMin == null || manual488ThreshMax == null) {
            IJ.log("Waiting for user to set threshold on conf488...");
            IJ.run(imp488, "Threshold...", "");
            WaitForUserDialog threshDialog = new WaitForUserDialog(
                    "Set Threshold",
                    "Adjust the threshold on the conf488 image, then click OK to use it for all images."
            );
            threshDialog.show();

            ImageProcessor ip = imp488.getProcessor();
            double minT = ip.getMinThreshold();
            double maxT = ip.getMaxThreshold();
            if (minT == ImageProcessor.NO_THRESHOLD || maxT == ImageProcessor.NO_THRESHOLD) {
                throw new RuntimeException("No threshold set. Please set a threshold and click OK.");
            }
            manual488ThreshMin = minT;
            manual488ThreshMax = maxT;
            IJ.log("Using manual conf488 threshold for run: min=" + manual488ThreshMin + ", max=" + manual488ThreshMax);
        }

        IJ.setThreshold(imp488, manual488ThreshMin, manual488ThreshMax);
        IJ.run(imp488, "Convert to Mask", "");
        IJ.run("Clear Results");
        rm.runCommand(imp488, "Measure");
        ResultsTable threshRt = ResultsTable.getResultsTable();
        int threshCount = threshRt.size();
        double[] threshArea = getColumnValues(threshRt, new String[]{"Area"});
        double[] threshMean = getColumnValues(threshRt, new String[]{"Mean"});
        double[] threshIntDen = getColumnValues(threshRt, new String[]{"IntDen"});
        double[] threshAreaPct = getColumnValues(threshRt, new String[]{"%Area", "AreaFrac", "AreaFraction"});
        double[] threshRawIntDen = getColumnValues(threshRt, new String[]{"RawIntDen", "RawInt"});

        int rowCount = Math.min(rawCount, threshCount);
        if (rawCount != threshCount) {
            IJ.log("Warning: Raw/Threshold row count mismatch for " + identifier +
                   " (raw=" + rawCount + ", thresh=" + threshCount + ")");
        }

        for (int i = 0; i < rowCount; i++) {
            addCombinedRow(combinedResults, identifier, i + 1,
                    rawArea[i], rawMean[i], rawIntDen[i], rawAreaPct[i], rawRawIntDen[i],
                    threshArea[i], threshMean[i], threshIntDen[i], threshAreaPct[i], threshRawIntDen[i]);
        }

        imp488.changes = false; // Prevent save dialog
        imp488.close();

        // Cleanup
        rm.reset();
        IJ.run("Clear Results");

        IJ.log("Successfully processed: " + identifier);
    }

    // ================================
    // Helper: Collect measurement columns safely
    // ================================
    private double[] getColumnValues(ResultsTable rt, String[] possibleNames) {
        int rowCount = rt.size();
        double[] values = new double[rowCount];
        for (int i = 0; i < rowCount; i++) {
            values[i] = getValueFromTable(rt, i, possibleNames);
        }
        return values;
    }

    private double getValueFromTable(ResultsTable rt, int row, String[] possibleNames) {
        for (String name : possibleNames) {
            if (rt.getColumnIndex(name) >= 0) {
                return rt.getValue(name, row);
            }
        }
        return Double.NaN;
    }

    private void addCombinedRow(ResultsTable combined, String identifier, int roiIndex,
                                double rawArea, double rawMean, double rawIntDen, double rawAreaPct, double rawRawIntDen,
                                double threshArea, double threshMean, double threshIntDen, double threshAreaPct, double threshRawIntDen) {
        int row = combined.getCounter();
        combined.incrementCounter();
        combined.setLabel(identifier, row);
        combined.addValue("ROI", roiIndex);

        combined.addValue("Raw_Area", rawArea);
        combined.addValue("Raw_Mean", rawMean);
        combined.addValue("Raw_IntDen", rawIntDen);
        combined.addValue("Raw_%Area", rawAreaPct);
        combined.addValue("Raw_RawIntDen", rawRawIntDen);

        combined.addValue("Thresh_Area", threshArea);
        combined.addValue("Thresh_Mean", threshMean);
        combined.addValue("Thresh_IntDen", threshIntDen);
        combined.addValue("Thresh_%Area", threshAreaPct);
        combined.addValue("Thresh_RawIntDen", threshRawIntDen);
    }

    // ================================
    // Helper: Extract number from filename
    // ================================
    private String extractNumberFromFilename(String filename) {
        // Pattern: conf405-<number>.tif or conf 405-<number>.tif (case insensitive)
        Pattern pattern = Pattern.compile("conf\\s?405\\s?-?\\s?(.*?)\\.(tif|tiff|stk)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
