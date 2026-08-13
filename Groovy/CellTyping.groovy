// AUTHOR: B282025
// MSc Bioinformatics Dissertation
// August 2026
//
// TITLE: Cell Typing
//
// DESCRIPTION: This script uses composite classifiers to classify cell 
// detections as positive or negative for specific protein markers. This
// script exports a file containing each cell detection and its
// Classification along with all detected measurements on each cell. 
//
// PREREQUISITE: Run TissueDetection.groovy, CellSegmentation.groovy,
// and define the vasculature compartment before runing this script. 
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.


// Import packages
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathObjects
import qupath.lib.roi.RoiTools
import qupath.lib.gui.commands.Commands


// Set the image type
setImageType('FLUORESCENCE')

// CELL TYPING
//
print 'Cell typing...'
runObjectClassifier("cell_typer_v2")


// TAG VASCULAR CELLS STRUCTURALLY (BY SPATIAL CONTAINMENT)
//
// Vascular cells are identified directly by spatial containment (cell 
// centroid inside a Vascular-classified annotation) and tagged via metadata, 
// independent of the object hierarchy.

print 'Tagging vascular cells structurally...'

def vascularAnnotations = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Vascular")
}

if (vascularAnnotations.isEmpty()) {
    print 'WARNING: No annotations with class "Vascular" found. No cells will be tagged as vascular.'
}

def allCells = getDetectionObjects().findAll { it.isCell() }
def vascularCellCount = 0

allCells.each { cell ->
    def centroid = cell.getROI().getGeometry().getCentroid()
    def isVascular = vascularAnnotations.any { it.getROI().getGeometry().contains(centroid) }
    // Written as its own column (rather than reusing "Parent") so the
    // downstream analysis no longer depends on hierarchy/parent-child state.
    cell.getMetadata().put("Structural Region", isVascular ? "Vascular" : "")
    if (isVascular) vascularCellCount++
}

print "Tagged ${vascularCellCount} cell(s) as vascular (by spatial containment)."


// ATTRIBUTE EACH CELL TO A NON-OBSTRUCTED/OBSTRUCTED (OR OTHER) TISSUE CATEGORY

print 'Assigning tissue categories to cells...'

def getAllDescendantCells(pathObject) {
    def cells = []
    pathObject.getChildObjects().each { child ->
        if (child.isCell()) {
            cells << child
        } else {
            cells.addAll(getAllDescendantCells(child))
        }
    }
    return cells
}

def categoryAnnotations = getAnnotationObjects().findAll { ann ->
    def name = ann.getName()
    name != null && !name.trim().isEmpty()
}

if (categoryAnnotations.isEmpty()) {
    print 'WARNING: No named category annotations were found. Category will be reported as "Unknown" for every cell.'
}

// Determine the category annotation containing a Tissue annotation's
// centroid.
def getCategoryAnnotation = { tissue ->
    def centroid = tissue.getROI().getGeometry().getCentroid()
    def matches = categoryAnnotations.findAll { it.getROI().getGeometry().contains(centroid) }
    if (matches.isEmpty()) return null
    if (matches.size() > 1) {
        def names = matches.collect { it.getName() }.join(', ')
        print "WARNING: Tissue section centroid falls within multiple overlapping category annotations (${names}). Using the first match."
    }
    return matches[0]
}

def tissueAnnotations = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Tissue")
}

if (tissueAnnotations.isEmpty()) {
    print 'WARNING: No annotations with class "Tissue" found. Skipping category assignment.'
} else {
    tissueAnnotations.eachWithIndex { tissue, tissueIndex ->
        def tissueLabel = "Tissue_${tissueIndex + 1}"
        def categoryAnn = getCategoryAnnotation(tissue)
        def category = categoryAnn?.getName() ?: "Unknown"
        def description = categoryAnn?.getDescription() ?: ""
        if (category == "Unknown") {
            print "WARNING: Could not determine category for ${tissueLabel} (centroid falls outside all category annotations)."
        }

        def cellsInTissue = getAllDescendantCells(tissue)
        cellsInTissue.each { cell ->
            cell.getMetadata().put("Tissue", tissueLabel)
            cell.getMetadata().put("Category", category)
            cell.getMetadata().put("Description", description)
        }
        print "Assigned ${cellsInTissue.size()} cells in ${tissueLabel} to category: ${category}"
    }
}

fireHierarchyUpdate()
print 'Category assignment completed.'


// SAVE AND EXPORT DETECTION MEASUREMENTS

print 'Saving and exporting detection measurements...'

def imageName = getProjectEntry() != null ? getProjectEntry().getImageName() : getCurrentImageData().getServer().getMetadata().getName()
def outputDir = buildFilePath(PROJECT_BASE_DIR, "measurements")
mkdirs(outputDir)
def outputFile = buildFilePath(outputDir, GeneralTools.stripExtension(imageName) + "_detection_measurements.tsv")

saveDetectionMeasurements(outputFile)

print "Detection measurements exported to: ${outputFile}"
print 'Done!'