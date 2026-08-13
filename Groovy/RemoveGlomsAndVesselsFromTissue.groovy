// Author: B282025
// MSc Bioinformatcs Dissertation
// August 2026
//
// TITLE: Remove Glomeruli and Vasculature from Tissue
//
// DESCRIPTION: This script subtracts the glomeruli and vascular compartments
// from the tissue annotations, thus creating a "tubular-interstitial" region
// for use in further analyses. 
// 
// PREREQUISITE: The tissue sections, vasular, and glomeruli cen be defined
// prior to running this script by importing the annotations, drawing them
// manually, or optionally, generating them here.
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.


// IMPORT LIBRARIES
import qupath.lib.roi.RoiTools
import qupath.lib.objects.PathObjects


// OPTIONAL: Define the vascularture and glomeruli here
//
// Define the vascular compartment
//selectObjectsByClassification("Tissue")
//print 'Defining the vascular compartment...'
//createAnnotationsFromPixelClassifier("FindVessels", 165.0, 2000000.0)

// Define the glomeruli compartment
//print 'Defining the glomeruli compartment...'
//selectObjectsByClassification("Tissue")
//createAnnotationsFromPixelClassifier("FindGlomeruli", 2000.0, 2000.0)

// Subtract the glomeruli and vascular objects (and their children) from the tissue annotation
print 'Subtracting vascular and glomeruli compartments from tissue...'

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def allAnnotations = getAnnotationObjects()

def tissueAnnotations = allAnnotations.findAll { it.getPathClass() == getPathClass("Tissue") }
def subtractAnnotations = allAnnotations.findAll {
    it.getPathClass() == getPathClass("Vascular") || it.getPathClass() == getPathClass("Glomeruli")
}

def toAdd = []

tissueAnnotations.each { tissue ->
    def tissueROI = tissue.getROI()
    def plane = tissueROI.getImagePlane()

    // Gather the ROIs of vascular/glomeruli objects on this plane
    def subtractROIs = subtractAnnotations.findAll {
        it.getROI().getImagePlane() == plane
    }.collect { it.getROI() }

    def newROI = tissueROI
    if (!subtractROIs.isEmpty()) {
        // Merge all the compartments to subtract into a single ROI, then subtract
        // that from the tissue ROI in one operation
        def mergedCompartments = RoiTools.union(subtractROIs)
        newROI = RoiTools.subtract(tissueROI, mergedCompartments)
    }

    toAdd << PathObjects.createAnnotationObject(newROI, tissue.getPathClass())
}

// Select old tissue + vascular/glomeruli annotations, then delete them and all descendants
selectObjects(tissueAnnotations + subtractAnnotations)
removeSelectedObjectsAndDescendants()

hierarchy.addObjects(toAdd)
resolveHierarchy()

print 'Done.'