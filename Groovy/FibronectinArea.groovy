// AUTHOR: B282025
// MSc Bioinformatics Dissertation
// August 2026
//
// TITLE: Fibronectin Stain Area Measurements by Tissue Compartment
//
// DESCRIPTION: This script runs the "fibronectin_thresholder8" pixel 
// classifier over the tissue annotations and every compartment within them, 
// measures the area of pixels it classifies as fibronectin positive, and
// exports one row per tissue section per compartment with the compartment's
// own area, the fibronectin-positive area within it, and the percentage.
//
// PREREQUISITE: Run TissueDetection.groovy and Compartmentalisation.groovy 
// on this image first. This script assumes "Tissue" annotations already 
// exist, each with "Vascular", "Glomeruli", "Tubule with Lumen", and 
// "Interstitium" compartments already defined, and that any hand-drawn 
// category annotations are still present so category/description can be 
// carried through to the export.
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.



// IMPORT PACKAGES
import qupath.lib.roi.RoiTools

// DEFINE VARIABLES
def classifierName = "fibronectin_thresholder8"
def measurementId = "Fibronectin_Stain"
def positiveClassName = "Fibronectin"

// Includes "Tissue" so we also get a whole-section fibronectin figure
// alongside the four compartments.
def compartmentClassNames = ["Vascular", "Glomeruli", "Tubule with Lumen", "Interstitium"]
def allMeasuredClassNames = ["Tissue"] + compartmentClassNames


// ASSIGN COMPARTMENTS TO TISSUE SECTIONS
//
// Assigns each compartment annotation to whichever Tissue section 
// it overlaps most, by intersection area.

def fixGeometry = { geom -> geom.isValid() ? geom : geom.buffer(0) }

def assignToBestTissue = { annotations, tissueList ->
    def assignment = [:]
    tissueList.each { assignment[it] = [] }
    def tissueGeometries = tissueList.collectEntries { [(it): fixGeometry(it.getROI().getGeometry())] }
    annotations.each { ann ->
        def annGeometry = fixGeometry(ann.getROI().getGeometry())
        def bestTissue = null
        def bestOverlap = 0.0
        tissueList.each { tissue ->
            def overlap = 0.0
            try {
                overlap = annGeometry.intersection(tissueGeometries[tissue]).getArea()
            } catch (Exception e) {
                print "WARNING: Could not compute overlap between ${ann} (class ${ann.getPathClass()}) and tissue section ${tissue} due to invalid geometry (${e.message}). Treating overlap as zero for this pair."
            }
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestTissue = tissue
            }
        }
        if (bestTissue != null) {
            assignment[bestTissue] << ann
        } else {
            print "WARNING: ${ann} (class ${ann.getPathClass()}) does not overlap any Tissue section. It will be excluded from the fibronectin export."
        }
    }
    return assignment
}

def tissueAnnotations = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Tissue")
}

if (tissueAnnotations.isEmpty()) {
    print 'WARNING: No annotations with class "Tissue" found. Did you run Compartmentalization.groovy on this image first? Aborting.'
    return
}

def compartmentAnnotations = getAnnotationObjects().findAll { ann ->
    ann.getPathClass()?.getName() in compartmentClassNames
}
def tissueToCompartments = assignToBestTissue(compartmentAnnotations, tissueAnnotations)


// RUN THE FIBRONECTIN CLASSIFIER AND ADD AREA MEASUREMENTS
//
// addPixelClassifierMeasurements() adds measurements (area + % per class
// the classifier outputs) to whatever's currently selected. We select the
// Tissue annotations plus every compartment annotation in one go, so the
// classifier only needs to run once and every object gets its own
// measurement based on its own ROI.
print "Measuring fibronectin stain with classifier '${classifierName}'..."
selectObjectsByClassification(*allMeasuredClassNames)
addPixelClassifierMeasurements(classifierName, measurementId)
print 'Fibronectin measurements added.'


// Finds the fibronectin-positive area measurement on an annotation without
// hardcoding the exact key format addPixelClassifierMeasurements()
def getFibronectinAreaUm2 = { ann ->
    def matchName = ann.getMeasurementList().getMeasurementNames().find { name ->
        def lower = name.toLowerCase()
        lower.contains(positiveClassName.toLowerCase()) && lower.contains("area") && !lower.contains("%")
    }
    if (matchName == null) {
        print "WARNING: No fibronectin area measurement found on ${ann} (class ${ann.getPathClass()}). Reporting 0."
        return 0.0
    }
    // Map-style accessor (same as used for cell measurements elsewhere in
    // this project) rather than getMeasurementValue(), which doesn't exist
    // on DefaultMeasurementList.
    def value = ann.measurements[matchName]
    return (value == null || value.isNaN()) ? 0.0 : value
}

// Compartment/tissue area, computed directly from calibrated geometry
// rather than parsed from a measurement string, for consistency with
// Compartmentalization.groovy's own area export.
def cal = getCurrentImageData().getServer().getPixelCalibration()
def pixelWidthUm = cal.getPixelWidthMicrons()
def pixelHeightUm = cal.getPixelHeightMicrons()
if (!cal.hasPixelSizeMicrons()) {
    print 'WARNING: Image lacks calibrated pixel size in microns. Areas will be reported in raw pixel^2 units instead of um^2.'
}
def pixelAreaUm2 = (pixelWidthUm ?: 1.0) * (pixelHeightUm ?: 1.0)
def getAreaUm2 = { roi -> roi.getGeometry().getArea() * pixelAreaUm2 }


// CATEGORY/DESCRIPTION LOOKUP
//
// Each Tissue section is tagged with whichever category
// annotation it overlaps the most.
def categoryAnnotations = getAnnotationObjects().findAll { ann ->
    def name = ann.getName()
    name != null && !name.trim().isEmpty()
}

if (categoryAnnotations.isEmpty()) {
    print 'WARNING: No named category annotations were found. Category will be reported as "Unknown" for every tissue section.'
}

def getCategoryAnnotation = { tissue ->
    if (categoryAnnotations.isEmpty()) return null
    def tissueGeometry = tissue.getROI().getGeometry()
    def overlaps = categoryAnnotations.collect { cat ->
        [cat: cat, overlap: tissueGeometry.intersection(cat.getROI().getGeometry()).getArea()]
    }.findAll { it.overlap > 0 }.sort { -it.overlap }
    if (overlaps.isEmpty()) return null
    if (overlaps.size() > 1) {
        def names = overlaps.collect { it.cat.getName() }.join(', ')
        print "WARNING: Tissue section overlaps multiple category annotations (${names}). Using the one with the largest overlap."
    }
    return overlaps[0].cat
}

def tsvEscape = { value ->
    def text = value?.toString() ?: ""
    return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}


// EXPORT
print 'Exporting fibronectin area measurements...'

def imageName = getProjectEntry() != null ? getProjectEntry().getImageName() : getCurrentImageData().getServer().getMetadata().getName()
def outputDir = buildFilePath(PROJECT_BASE_DIR, "measurements")
mkdirs(outputDir)
def outputFile = new File(outputDir, GeneralTools.stripExtension(imageName) + "_fibronectin_areas.tsv")

def rows = []
rows << ["Image", "Tissue", "Category", "Description", "Compartment", "Compartment_Area_um2", "Fibronectin_Area_um2", "Percent_Fibronectin"].join('\t')

tissueAnnotations.eachWithIndex { tissue, tissueIndex ->
    def tissueLabel = "Tissue_${tissueIndex + 1}"
    def categoryAnn = getCategoryAnnotation(tissue)
    def category = categoryAnn?.getName() ?: "Unknown"
    def description = categoryAnn?.getDescription() ?: ""
    if (category == "Unknown") {
        print "WARNING: Could not determine category for ${tissueLabel} (it doesn't overlap any named category annotation)."
    }

    // Row for the whole tissue section
    def tissueAreaUm2 = getAreaUm2(tissue.getROI())
    def tissueFibronectinUm2 = getFibronectinAreaUm2(tissue)
    def tissuePercent = tissueAreaUm2 > 0 ? (100.0 * tissueFibronectinUm2 / tissueAreaUm2) : 0.0
    rows << [imageName, tissueLabel, category, description, "Tissue", tissueAreaUm2, tissueFibronectinUm2, tissuePercent]
        .collect { tsvEscape(it) }.join('\t')

    def compartmentsForTissue = tissueToCompartments[tissue] ?: []

    compartmentClassNames.each { compClassName ->
        def compartmentAnns = compartmentsForTissue.findAll { it.getPathClass()?.getName() == compClassName }

        def compartmentAreaUm2 = compartmentAnns.sum { getAreaUm2(it.getROI()) } ?: 0.0
        def fibronectinAreaUm2 = compartmentAnns.sum { getFibronectinAreaUm2(it) } ?: 0.0
        def percentFibronectin = compartmentAreaUm2 > 0 ? (100.0 * fibronectinAreaUm2 / compartmentAreaUm2) : 0.0

        rows << [imageName, tissueLabel, category, description, compClassName, compartmentAreaUm2, fibronectinAreaUm2, percentFibronectin]
            .collect { tsvEscape(it) }.join('\t')
    }
}

outputFile.text = rows.join(System.lineSeparator()) + System.lineSeparator()
print "Fibronectin area measurements exported to: ${outputFile.getAbsolutePath()}"

print 'Done!'