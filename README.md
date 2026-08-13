# Fibronectin Expression in the Human Kidney: A Multi-Omic Spatial Analysis
### University of Edinburgh
### August 2026

This code accompanies my MSc Bioinformatics dissertation project investigating the cellular sources and spatial distribution of fibronectin in human kidney tissue, using Akoya multiplex immunofluorescent spatial protein imaging and CosMx 6K spatial transcriptomics.

My study addressed three aims:

1. Define the cellular associations and compartmental distribution of fibronectin in human kidney tissue using multiplex spatial protein imaging.
2. Identify fibronectin-rich tissue niches and determine whether these regions are enriched for cells and markers associated with pathogenic tissue remodelling.
3. Integrate spatial protein imaging with CosMx 6K spatial transcriptomics to compare fibronectin protein and *FN1* transcript localisation.

## Repository structure

```
Groovy/         QuPath scripts used to automate image analysis
Classifiers/    Trained QuPath pixel and object classifiers (.json)
Python/         Python notebooks for downstream statistical analysis and figures
R/              R script for converting the CosMx Seurat object to AnnData
Environment/    Conda environment specification
```

### `Groovy/`

| Script | Purpose |
|---|---|
| `TissueDetection.groovy` | Automated detection of tissue sections using the trained tissue pixel classifier |
| `Compartmentalisation.groovy` | Sequential segmentation of vascular, glomerular, tubular, and interstitial compartments |
| `CellSegmentation.groovy` | Cell segmentation and quality control filtering of detections |
| `CellTyping.groovy` | Applies the composite cell-type classifier and exports a classification string per cell |
| `ImportCellTypes.groovy` | Imports cell-type annotations (from Python) back into QuPath for visualisation |
| `RemoveGlomsAndVesselsFromTissue.groovy` | Subtracts glomerular and vascular annotations to isolate the tubulointerstitial compartment |
| `ExportNicheCellTypes.groovy` | Generates fibronectin niche annotations, performs cell typing, labels cells as part of a fibronectin niche or not, and exports cell classifications |
| `FibronectinArea.groovy` | Automates detection of fibronectin-positive pixels and exports compartmental area measurements |

### `Classifiers/`

| File | Used for |
|---|---|
| `FindTissueClassifier.json` | Whole-slide tissue detection |
| `FindVessels.json` | Vascular compartment |
| `FindGlomeruli.json` | Glomerular compartment |
| `FindTubules.json` | Tubular compartment |
| `ExcludeInterstitium2.json` | Removal of interstitial signal from the preliminary tubular annotation |
| `cell_typer_v2.json` | Composite object classifier for cell typing |
| `fibronectin_thresholder8.json` | Fixed-threshold pixel classifier for fibronectin-positive area |
| `DetectFibronectinCells.json` | Object classifier for fibronectin-positive cells |
| `fn_density_map4.json` | Density map settings used to identify fibronectin-positive niches |

### `Python/`

| Notebook | Purpose |
|---|---|
| `compartment_analysis.ipynb` | Generates plots and runs statistical analysis on fibronectin positive areas in tissue compartments |
| `cell_type_analysis.ipynb` | Parses QuPath classification strings into final cell-type calls; generates plots to analyse fibronectin positive cells |
| `niche_cell_type_analysis.ipynb` | Cell-type enrichment analysis within fibronectin-positive niches |
| `col_in_fn_niche_analysis.ipynb` | Paired analysis of collagen I/III intensity within fibronectin niches vs. non-niche tubulointerstitial tissue |
| `cosmx_analysis.ipynb` | Exploration of CosMx data and analysis of *FN1* expression |

### `R/`

| Script | Purpose |
|---|---|
| `convert_spatial_to_anndata.Rmd` | Converts the processed CosMx Seurat object (Reck et al.) into a Python-compatible AnnData object |

### `Environment/`

| File | Purpose |
|---|---|
| `kidney-st.yml` | Conda environment used for CosMx spatial transcriptomic analysis |

## Workflow

The image analysis pipeline follows this order (see Figure 10 of the dissertation for the full flowchart):

1. **Tissue detection** — `TissueDetection.groovy` + `FindTissueClassifier.json`
2. **Compartmentalisation** — `Compartmentalisation.groovy` + `FindVessels.json`, `FindGlomeruli.json`, `FindTubules.json`, `ExcludeInterstitium2.json`, then `FibronectinArea.groovy` + `fibronectin_thresholder8.json`, and `compartment_analysis.ipynb`
3. **Cell typing** — `CellSegmentation.groovy`, followed by `CellTyping.groovy` + `cell_typer_v2.json`, followed by `cell_type_analysis.ipynb`
4. **Niche analysis** — `RemoveGlomsAndVesselsFromTissue.groovy`, followed by `CellSegmentation.groovy`, followed by `ExportNicheCellTypes.groovy` + `fn_density_map4.json`, then `niche_cell_type_analysis.ipynb` and `col_in_fn_niche_analysis.ipynb`
5. **CosMx integration** — `convert_spatial_to_anndata.Rmd`, followed by `cosmx_analysis.ipynb`

Classifier files and Groovy scripts are designed to run within a QuPath Project file structure; they are not standalone.

## Data availability

The nephrectomy immunofluorescence images, associated QuPath project files, and image data are not included in this repository, as they are part of an ongoing, unpublished study and cannot be shared publicly at this time.

The processed CosMx 6K spatial transcriptomic data is publicly available from the NCBI Gene Expression Omnibus under accession [GSE282059](https://www.ncbi.nlm.nih.gov/geo/query/acc.cgi?acc=GSE282059), and is described in Reck et al., *Nat Commun* 2025 (doi:10.1038/s41467-025-59997-4).

## Acknowledgements

This work was completed as part of an MSc dissertation under the supervision of Dr David Baird, University of Edinburgh.
