// Author: B282025
// MSc Bioinformatcs Dissertation
// August 2026
//
// TITLE: Export Cell Types from Fibronectin Niches
//
// DESCRIPTION: This script draws annotations around fibronectin hotspots
// in the tubulointerstitial compartment, performs cell typing and exports 
// the cell classifications.
//
// PREREQUISITE: Prior to running this script, tissue sections must be defined
// with the vasulcature and gloms removed (RemoveGlomsAndVesselsFromTissue.groovy).
// Cells must be segmented (CellSegmentation.groovy).
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.


// IMPORT LIBRARIES
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject

// Set the image type
setImageType('FLUORESCENCE')

// CELL TYPING
//
selectObjectsByClassification("Tissue");
print 'Cell typing...'
runObjectClassifier("cell_typer_v2")

// LOCATE FIBROTIC NICHES
createAnnotationsFromDensityMap("fn_density_map4", [0: 75.0], "Fibronectin")
selectObjectsByClassification("Tissue");

// Resolving the hierarchy inserts cells under the parent tissue/niche objects
resolveHierarchy()


// ATTRIBUTE EACH CELL TO A HEALTHY/UNHEALTHY (OR OTHER) TISSUE CATEGORY,
// AND TO FIBRONECTIN NICHE STATUS
//
// Niche status is determined from each cell's immediate parent: if a 
// cell's direct parent annotation is classified "Fibronectin", the cell 
// is labeled "Fibronectin Niche"; otherwise it is labeled 
// "Non-Fibronectin Niche". 

print 'Assigning tissue categories and niche status to cells...'

// Recursively collect all cell descendants of an object, not just direct
// children - needed because cells inside a Fibronectin niche annotation are
// nested one level deeper than other cells.
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

def fibronectinClass = getPathClass("Fibronectin")

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
    print 'WARNING: No annotations with class "Tissue" found. Skipping category/niche assignment.'
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
        def nicheCount = 0
        cellsInTissue.each { cell ->
            cell.getMetadata().put("Tissue", tissueLabel)
            cell.getMetadata().put("Category", category)
            cell.getMetadata().put("Description", description)

            def parent = cell.getParent()
            def isInNiche = parent != null && parent.getPathClass() == fibronectinClass
            cell.getMetadata().put("Niche", isInNiche ? "Fibronectin Niche" : "Non-Fibronectin Niche")
            if (isInNiche) nicheCount++
        }
        print "Assigned ${cellsInTissue.size()} cells in ${tissueLabel} to category: ${category} (${nicheCount} in Fibronectin niches)"
    }
}

fireHierarchyUpdate()
print 'Category and niche assignment completed.'


// SAVE DATA AND EXPORT DETECTION MEASUREMENTS (CLASSIFICATION + METADATA ONLY)
//
// MeasurementExporter reads measurements from the project's saved image
// data, so the current image data must be saved first or the metadata
// added above won't show up in the export.

print 'Saving image data...'

def entry = getProjectEntry()
if (entry == null) {
    print 'ERROR: No project entry found. This script must be run on an image within a QuPath project so that MeasurementExporter can read the saved data.'
    return
}
entry.saveImageData(getCurrentImageData())

print 'Exporting detection measurements (Class + metadata columns only)...'

def imageName = entry.getImageName()
def outputDir = buildFilePath(PROJECT_BASE_DIR, "measurements")
mkdirs(outputDir)
def outputFile = new File(buildFilePath(outputDir, GeneralTools.stripExtension(imageName) + "_detection_measurements.tsv"))


def columnsToInclude = ["Image", "Object ID", "Object type", "Classification", "Tissue", "Category", "Description", "Niche"] as String[]

new MeasurementExporter()
    .imageList([entry])
    .separator("\t")
    .includeOnlyColumns(columnsToInclude)
    .exportType(PathCellObject.class)
    .exportMeasurements(outputFile)

print "Detection measurements exported to: ${outputFile}"
print 'Done!'