// Author: B282025
// MSc Bioinformatcs Dissertation
// August 2026
//
// TITLE: Import Cell Types
//
// DESCRIPTION: This script imports a file with the cell Object IDs and uses 
// the cell types determined by cell_type_analysis.ipynb or 
// niche_cell_type_analysis.ipynb to color the cells by type for the image 
// that is currently open in QuPath. 
// 
// PREREQUISITE: CellSegmentation.groovy and either CellTyping.groovy or 
// ExportNicheCellTypes.groovy must be run before this script. Change the
// input file path to the appropriate file. This script expects a TSV with at 
// least an "Object ID" column (the same IDs QuPath originally exported) and a 
// "Cell Type" column. This script matches cells in the currently open image
// by Object ID.
//
// REFERENCES: This code was written using a combination of the QuPath GUI 
// workflow to script functionality, Claude Sonnet 5, and manual scripting.

// DEFINE YOUR INPUT FILE
def inputFile = new File("../measurements/PPJ_07_2_Scan1_DB_fn_niche_detection_measurements_with_celltype.tsv")

def idCol = -1
def typeCol = -1
def cellTypeById = [:]

boolean first = true
inputFile.eachLine { line ->
    def fields = line.split('\t', -1)
    if (first) {
        idCol = fields.findIndexOf { it == "Object ID" }
        typeCol = fields.findIndexOf { it == "Cell Type" }
        if (idCol == -1 || typeCol == -1) {
            throw new RuntimeException("Could not find 'Object ID' and/or 'Cell Type' columns in the header.")
        }
        first = false
        return
    }
    if (fields.size() > Math.max(idCol, typeCol)) {
        cellTypeById[fields[idCol]] = fields[typeCol]
    }
}
print "Loaded ${cellTypeById.size()} cell type assignment(s) from file."

// Apply to every cell in the currently open image
def cells = getCellObjects()
int matched = 0
int missing = 0
cells.each { cell ->
    def id = cell.getID().toString()
    def cellType = cellTypeById[id]
    if (cellType != null) {
        cell.setPathClass(getPathClass(cellType))
        matched++
    } else {
        missing++
    }
}

fireHierarchyUpdate()
print "Applied Cell Type classification to ${matched} cell(s) in this image."
print "${missing} cell(s) in this image had no matching row in the file (check the TSV covers this image)."