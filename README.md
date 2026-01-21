# Conf405/488 Analysis Plugin for ImageJ

An automated ImageJ plugin for processing paired conf405 and conf488 microscopy images with cell detection and intensity measurements.

## Features

- Automatic detection of cells in conf405 images using thresholding and particle analysis
- Interactive ROI review and adjustment for each image
- Automated measurement of both raw and thresholded conf488 intensities
- Batch processing of multiple image pairs
- Organized output with individual folders for each image pair

## Installation

1. Download [`Conf405_488_Plugin.zip`](Conf405_488_Plugin.zip)
2. Extract all `.class` files from the zip
3. Copy the extracted `.class` files to your ImageJ `plugins/` folder
4. Restart ImageJ
5. Find "Conf405 488" in the **Plugins** menu

**ImageJ plugins folder locations:**
- **Windows**: `C:\Program Files\ImageJ\plugins\`
- **Mac**: `/Applications/ImageJ.app/plugins/`
- **Linux**: `~/ImageJ/plugins/`

## Usage

### File Requirements

Your images must follow this naming convention:
- `conf405-1.tif`, `conf488-1.tif`
- `conf405-2.tif`, `conf488-2.tif`
- etc.

Supported formats: `.tif`, `.tiff`, `.stk`

### Running the Plugin

1. Open ImageJ and go to **Plugins → Conf405 488**
2. Click **"Run Conf405/488 Analysis"**
3. Select the folder containing your image pairs
4. For each image pair:
   - The plugin will detect cells in the conf405 image
   - A dialog will appear showing the detected cells
   - Review and adjust ROIs as needed:
     - Delete unwanted ROIs in the ROI Manager
     - Add new ROIs using drawing tools
     - Modify or reorder ROIs
   - Click **"OK - Continue"** to process the next image
   - Click **"Cancel - Stop"** to stop processing remaining images
5. Results are saved automatically in a `Results/` subfolder

### Output

For each image pair, the plugin creates a subfolder containing:
- `ROIset.zip` - Saved ROIs
- `Results405.csv` - Cell detection measurements from conf405
- `Results_raw.csv` - Raw conf488 intensity measurements
- `Results_thresh.csv` - Thresholded conf488 intensity measurements

## Default Settings

- **Minimum particle size**: 100 pixels
- **Threshold method**: Default dark
- **Measurements**: Area, Mean, Integrated Density, Area Fraction

## Troubleshooting

**Plugin doesn't appear in menu:**
- Ensure the class name has an underscore: `Conf405_488.class`
- Make sure ALL `.class` files (including `Conf405_488$1.class` and `Conf405_488$2.class`) are copied
- Restart ImageJ

**No images found:**
- Check your file naming matches the expected format
- Ensure conf405 and conf488 pairs have matching numbers
- Supported extensions: `.tif`, `.tiff`, `.stk`

**Images won't close:**
- Images should close automatically without save prompts
- If prompted, the plugin has been updated to prevent this

## Version

**Version 1.0** - Initial release

## Author

Gavin Huang

## License

Free to use for research purposes.
