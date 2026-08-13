// AUTHOR: B282025
// MSc Bioinformatics Dissertation
// August 2026
//
// TITLE: Tissue Compartmentalisation
//
// DESCRIPTION: The purpose of this script is to divide up kidney tissue 
// sections into broad "compartments" based on the anatomy of the kidney.
// These compartments include: glomeruli, vasculature, tubular epithelia,
// and interstitium.
// 
// PREREQUISITE: This script should be run after the tissue sections have
// been labeled and detected using TissueDetection.groovy
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.


// IMPORT PACKAGES
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathObjects
import qupath.lib.roi.RoiTools
import qupath.lib.gui.commands.Commands

// SET THE IMAGE TYPE
setImageType('FLUORESCENCE')

// SELECT THE TISSUE
selectObjectsByClassification("Tissue")

// DEFINE THE COMPARTMENTS
//
// Define the Vascular Compartment
print 'Defining the vascular compartment...'
createAnnotationsFromPixelClassifier("FindVessels", 165.0, 2000000.0, "INCLUDE_IGNORED")
// If vasculature needs to be manually adjusted, run the script up to this 
// point, make the adjustments, then proceed with the rest of the script. 

// Define the Glomeruli Compartment
print 'Defining the glomeruli compartment...'
selectObjectsByClassification("Ignore*")
createAnnotationsFromPixelClassifier("FindGlomeruli", 2000.0, 2000.0, "INCLUDE_IGNORED")

// Create the Tubular Compartment
print 'Defining the tubular compartment...'
selectObjectsByClassification("Ignore2*")
createAnnotationsFromPixelClassifier("FindTubules", 165.0, 2000000.0)

// Extract interstitium from tubules
print 'Extracting interstitium from the tubules...'
selectObjectsByClassification("Tubular")
createAnnotationsFromPixelClassifier("ExcludeInterstitium2", 20.0, 20.0)


// ASSIGN COMPARTMENT ANNOTATIONS TO TISSUE SECTIONS
//
// Assigns each compartment annotation to whichever tissue section it
// overlaps most (by intersection area).

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
            print "WARNING: ${ann} (class ${ann.getPathClass()}) does not overlap any Tissue section. It will be excluded from the interstitium calculation and area export."
        }
    }
    return assignment
}


// CLIP COMPARTMENTS TO THEIR TISSUE SECTION
//
// createAnnotationsFromPixelClassifier() masks new annotations to whatever
// was selected when it ran, but pixel classification is rasterized at a
// given resolution, so boundary pixels near the parent ROI's edge can
// still bleed a few pixels past it. Intersect each compartment annotation 
// with its assigned tissue's ROI, and replace it with the clipped version.

print 'Clipping compartments to their tissue section boundary...'

def clipCompartmentsToTissue = { tissueToCompartmentsMap ->
    def toRemove = []
    def replacements = []
    tissueToCompartmentsMap.each { tissue, compartments ->
        def tissueROI = tissue.getROI()
        compartments.eachWithIndex { ann, idx ->
            def clippedROI
            try {
                clippedROI = RoiTools.combineROIs(ann.getROI(), tissueROI, RoiTools.CombineOp.INTERSECT)
            } catch (Exception e) {
                print "WARNING: Could not clip ${ann} (class ${ann.getPathClass()}) to tissue section ${tissue} (${e.message}). Leaving it unclipped."
                return
            }
            if (clippedROI == null || clippedROI.isEmpty()) {
                print "WARNING: ${ann} (class ${ann.getPathClass()}) had no overlap with its assigned tissue after clipping. Removing it."
                toRemove << ann
                return
            }
            def originalArea = ann.getROI().getGeometry().getArea()
            def clippedArea = clippedROI.getGeometry().getArea()
            if (clippedArea < originalArea * 0.999) {
                def newAnn = PathObjects.createAnnotationObject(clippedROI, ann.getPathClass())
                toRemove << ann
                replacements << [old: ann, newAnn: newAnn, tissue: tissue]
                compartments[idx] = newAnn
                print "Clipped ${ann.getPathClass()} annotation to tissue section ${tissue} (${String.format('%.1f', originalArea - clippedArea)} px^2 removed)."
            }
        }
    }
    removeObjects(toRemove, false)
    replacements.each { entry -> entry.tissue.addChildObject(entry.newAnn) }
    if (!toRemove.isEmpty()) fireHierarchyUpdate()
}

def tissueAnnotationsForClipping = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Tissue")
}
def compartmentsToClip = getAnnotationObjects().findAll { ann ->
    ann.getPathClass()?.getName() in ["Tubule with Lumen", "Glomeruli", "Vascular"]
}
clipCompartmentsToTissue(assignToBestTissue(compartmentsToClip, tissueAnnotationsForClipping))


// DEFINE THE INTERSTITIUM PER TISSUE SECTION
//
// For each Tissue annotation, we take the Vascular, Glomeruli, and Tubule
// with Lumen annotations assigned to it above, merge their ROIs, then
// subtract from the tissue ROI using RoiTools.subtract().

print 'Defining the interstitium...'

def tissueAnnotations = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Tissue")
}

if (tissueAnnotations.isEmpty()) {
    print 'WARNING: No annotations with class "Tissue" found. Skipping interstitium creation.'
} else {
    def preInterstitiumCompartments = getAnnotationObjects().findAll { ann ->
        ann.getPathClass()?.getName() in ["Tubule with Lumen", "Glomeruli", "Vascular"]
    }
    def tissueToCompartments = assignToBestTissue(preInterstitiumCompartments, tissueAnnotations)

    tissueAnnotations.each { tissue ->
        def tissueROI = tissue.getROI()

        def compartmentROIs = (tissueToCompartments[tissue] ?: []).collect { it.getROI() }

        if (compartmentROIs.isEmpty()) {
            print "WARNING: No compartment annotations found within tissue section ${tissue}. Skipping interstitium for this section."
            return
        }

        def interstitiumROI = null
        try {
            // Merge all compartment ROIs into a single combined ROI
            def mergedCompartments = RoiTools.union(compartmentROIs)

            // Subtract the merged compartments from the tissue ROI
            interstitiumROI = RoiTools.subtract(tissueROI, mergedCompartments)
        } catch (Exception e) {
            print "WARNING: Failed to compute interstitium for tissue section ${tissue} due to invalid geometry (${e.message}). Skipping this section."
            return
        }

        if (interstitiumROI == null || interstitiumROI.isEmpty()) {
            print "WARNING: Interstitium ROI is empty for tissue section ${tissue}. Skipping."
            return
        }

        // Create the interstitium annotation and nest it under this tissue section
        def interstitiumAnnotation = PathObjects.createAnnotationObject(
            interstitiumROI,
            getPathClass("Interstitium")
        )
        tissue.addChildObject(interstitiumAnnotation)
        print "Interstitium defined for tissue section: ${tissue}"
    }

    fireHierarchyUpdate()
}

// DELETE THE TEMPORARY/IGNORED ANNOTATIONS

print 'Removing temporary/ignored annotations...'
def tempAnnotations = getAnnotationObjects().findAll { ann ->
    ann.getPathClass()?.getName() in ["Ignore*", "Ignore2*", "Tubular"]
}
removeObjects(tempAnnotations, true)
fireHierarchyUpdate()


print 'Compartmentalization completed.'