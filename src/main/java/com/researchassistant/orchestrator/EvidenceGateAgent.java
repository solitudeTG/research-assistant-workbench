package com.researchassistant.orchestrator;

public interface EvidenceGateAgent {

    CuratedEvidenceSet gate(String question, CuratedEvidenceSet hygienicCandidates);
}
