package com.ri.artificial.tools;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ri
 * @date 2026-10-04 14:37
 * 文档查询工具
 */

public class DocumentSearchTool {

    private final VectorStore vectorStore;

    public DocumentSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String search(String query) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(5).build());

        return docs.stream().map(Document::getText)
                .collect(Collectors.joining(""));
    }
}
