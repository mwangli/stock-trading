// AI_GENERATE_START -
package com.stock.console.documents;

import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(documents.list());
    }

    @GetMapping("/content")
    public ApiResponse<DocumentService.Document> content(@RequestParam String id) {
        return ApiResponse.ok(documents.get(id));
    }

    @GetMapping("/search")
    public ApiResponse<List<DocumentService.SearchHit>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(documents.search(query, limit));
    }
}
// AI_GENERATE_END -
