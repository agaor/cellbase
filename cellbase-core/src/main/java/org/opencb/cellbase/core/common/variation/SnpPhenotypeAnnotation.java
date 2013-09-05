package org.opencb.cellbase.core.common.variation;

// Generated Jun 5, 2012 6:41:13 PM by Hibernate Tools 3.4.0.CR1


/**
 * SnpPhenotypeAnnotation generated by hbm2java
 */
public class SnpPhenotypeAnnotation implements java.io.Serializable {

	private int snpPhenotypeAnnotationId;
//	private Snp snp;
	private String source;
	private String associatedGeneName;
	private String associatedVariantRiskAllele;
	private double riskAlleleFrequencyInControls;
	private double PValue;
	private String phenotypeName;
	private String phenotypeDescription;
	private String studyName;
	private String studyType;
	private String studyUrl;
	private String studyDescription;

	public SnpPhenotypeAnnotation() {
	}

	public SnpPhenotypeAnnotation(int snpPhenotypeAnnotationId, 
//			Snp snp,
			String source, String associatedGeneName,
			String associatedVariantRiskAllele,
			double riskAlleleFrequencyInControls, double PValue,
			String phenotypeName, String phenotypeDescription,
			String studyName, String studyType, String studyUrl,
			String studyDescription) {
		this.snpPhenotypeAnnotationId = snpPhenotypeAnnotationId;
//		this.snp = snp;
		this.source = source;
		this.associatedGeneName = associatedGeneName;
		this.associatedVariantRiskAllele = associatedVariantRiskAllele;
		this.riskAlleleFrequencyInControls = riskAlleleFrequencyInControls;
		this.PValue = PValue;
		this.phenotypeName = phenotypeName;
		this.phenotypeDescription = phenotypeDescription;
		this.studyName = studyName;
		this.studyType = studyType;
		this.studyUrl = studyUrl;
		this.studyDescription = studyDescription;
	}

	public int getSnpPhenotypeAnnotationId() {
		return this.snpPhenotypeAnnotationId;
	}

	public void setSnpPhenotypeAnnotationId(int snpPhenotypeAnnotationId) {
		this.snpPhenotypeAnnotationId = snpPhenotypeAnnotationId;
	}

//	public Snp getSnp() {
//		return this.snp;
//	}
//
//	public void setSnp(Snp snp) {
//		this.snp = snp;
//	}

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getAssociatedGeneName() {
		return this.associatedGeneName;
	}

	public void setAssociatedGeneName(String associatedGeneName) {
		this.associatedGeneName = associatedGeneName;
	}

	public String getAssociatedVariantRiskAllele() {
		return this.associatedVariantRiskAllele;
	}

	public void setAssociatedVariantRiskAllele(
			String associatedVariantRiskAllele) {
		this.associatedVariantRiskAllele = associatedVariantRiskAllele;
	}

	public double getRiskAlleleFrequencyInControls() {
		return this.riskAlleleFrequencyInControls;
	}

	public void setRiskAlleleFrequencyInControls(
			double riskAlleleFrequencyInControls) {
		this.riskAlleleFrequencyInControls = riskAlleleFrequencyInControls;
	}

	public double getPValue() {
		return this.PValue;
	}

	public void setPValue(double PValue) {
		this.PValue = PValue;
	}

	public String getPhenotypeName() {
		return this.phenotypeName;
	}

	public void setPhenotypeName(String phenotypeName) {
		this.phenotypeName = phenotypeName;
	}

	public String getPhenotypeDescription() {
		return this.phenotypeDescription;
	}

	public void setPhenotypeDescription(String phenotypeDescription) {
		this.phenotypeDescription = phenotypeDescription;
	}

	public String getStudyName() {
		return this.studyName;
	}

	public void setStudyName(String studyName) {
		this.studyName = studyName;
	}

	public String getStudyType() {
		return this.studyType;
	}

	public void setStudyType(String studyType) {
		this.studyType = studyType;
	}

	public String getStudyUrl() {
		return this.studyUrl;
	}

	public void setStudyUrl(String studyUrl) {
		this.studyUrl = studyUrl;
	}

	public String getStudyDescription() {
		return this.studyDescription;
	}

	public void setStudyDescription(String studyDescription) {
		this.studyDescription = studyDescription;
	}

}