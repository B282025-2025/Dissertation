// AUTHOR: B282025
// Bioinformatics MSc Dissertation
// August 2026
//
// TITLE: Cell Segmentation and Quality Control Filtering
//
// DESCRIPTION: This script segments cells using the Instanseg model and
// a selection of nuclear and cytoplasmic protein markers. Cell detections
// smaller than 8µm^2 and containing no nuclear DRAQ5 stain are deleted.
//
// PREREQUISITE: Tissue sections should be defined using
// TissueDetection.groovy or RemoveGlomsAndVesselsFromTissue.groovy prior 
// to running this script.
//
// REFERENCES: This code was written using a combination of the QuPath GUI
// workflow to script functionality, Claude Sonnet 5, and manual scripting.


// Helper function to count cell detections nested under the currently 
// selected objects.
def countCells = {
    return getSelectedObjects()
        .collectMany { it.getChildObjects() }
        .findAll { it.isCell() }
        .size()
}

// Select the tissue annotations
selectObjectsByClassification("Tissue")

// Run InstanSeg
qupath.ext.instanseg.core.InstanSeg.builder()
    .modelPath("/Users/gracenewman/Library/CloudStorage/OneDrive-UniversityofEdinburgh/Dissertation/Akoya_Data/Segmentation_Models/downloaded/fluorescence_nuclei_and_cells-0.1.1")
    .device("mps")
    .inputChannels([ColorTransforms.createChannelExtractor("Vimentin"), ColorTransforms.createChannelExtractor("CD45"), ColorTransforms.createChannelExtractor("Beta-actin"), ColorTransforms.createChannelExtractor("DRAQ5")])
    .outputChannels()
    .tileDims(512)
    .interTilePadding(32)
    .nThreads(4)
    .makeMeasurements(true)
    .randomColors(false)
    .outputType("Default")
    .build()
    .detectObjects()

print "Cells detected: ${countCells()}"

// Remove fragments
print 'Removing cells smaller than 8µm^2'
def fragments = getSelectedObjects()
    .collectMany { it.getChildObjects() }
    .findAll { it.isCell() }
    .findAll { cell ->
        def area = cell.measurements["Cell: Area µm^2"]
        return area == null || area.isNaN() || area < 8.0
    }
removeObjects(fragments, true)
fireHierarchyUpdate()
print "Cells removed as fragments (<8µm^2): ${fragments.size()}"
print "Cells remaining: ${countCells()}"

// Remove phantom cells
print 'Removing phantom cells...'
def phantomCells = getSelectedObjects()
    .collectMany { it.getChildObjects() }
    .findAll { it.isCell() }
    .findAll { cell ->
        def draq5 = cell.measurements["Nucleus: DRAQ5: Mean"]
        return draq5 == null || draq5.isNaN() || draq5 < 2
    }
removeObjects(phantomCells, true)
fireHierarchyUpdate()
print "Cells removed as phantoms (no DRAQ5): ${phantomCells.size()}"
print "Cells remaining: ${countCells()}"

print 'Done!'