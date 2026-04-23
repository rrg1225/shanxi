package com.aidecomposer.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class PublicKnowledgeGraphResponse {
    private List<PublicKnowledgeGraphNode> nodes = new ArrayList<>();
    private List<PublicKnowledgeGraphLink> links = new ArrayList<>();

    public List<PublicKnowledgeGraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<PublicKnowledgeGraphNode> nodes) {
        this.nodes = nodes;
    }

    public List<PublicKnowledgeGraphLink> getLinks() {
        return links;
    }

    public void setLinks(List<PublicKnowledgeGraphLink> links) {
        this.links = links;
    }
}
