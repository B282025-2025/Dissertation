// Author: B282025
// MSc Bioinformatcs Dissertation
// August 2026
//
// TITLE: Tissue Detection
//
// DESCRIPTION: This script uses all of the working protein markers to identify 
// regions of tissue and regions to ignore. 
// 
// PREREQUISITE: Before running this script, draw rough annotations around your
// sections to be analyzed. Labels and descriptions can be added to each rough 
// annotation to be included in the downstream data exportations.
//
// POSTREQUISITE: After runnng this script, review the tissue annotations. If any
// imaging and/or fixation artifacts should be removed, unlock the annotations and 
// manually remove the problematic areas.
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality and manual scripting.

// Set the image type
setImageType('FLUORESCENCE')

// SELECT THE ROUGH ANNOTATIONS
selectObjectsByClassification(null)

// DETECT THE TISSUE
createAnnotationsFromPixelClassifier("FindTissueClassifier", 5000000.0, 100000.0, "SPLIT")
print 'Kidney Tissue Sections Detected.'

selectObjectsByClassification("Tissue")