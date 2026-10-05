package com.thesis.elearning.modules.knowledge.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.knowledge.dto.AddPrerequisiteRequest;
import com.thesis.elearning.modules.knowledge.dto.CreateNodeRequest;
import com.thesis.elearning.modules.knowledge.dto.KnowledgeNodeDto;
import com.thesis.elearning.modules.knowledge.dto.StudentNodeStatusDto;
import com.thesis.elearning.modules.knowledge.service.KnowledgeNodeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Knowledge graph endpoints.
 *
 * <h2>Two tiers of authorisation</h2>
 * Reads live under {@code /knowledge/**} and need only an authenticated
 * principal: students must be able to open their own skill tree. Writes carry
 * {@link PreAuthorize} on top of the {@code /teacher/**} path prefix, so a
 * controller mounted elsewhere cannot inherit protection by accident.
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeNodeController {

    private final KnowledgeNodeService nodeService;

    public KnowledgeNodeController(final KnowledgeNodeService nodeService) {
        this.nodeService = nodeService;
    }

    /* ----------------------------- Read ----------------------------- */

    @GetMapping("/nodes")
    public ApiResponse<List<KnowledgeNodeDto>> list() {
        return ApiResponse.success(
                nodeService.findAll(), "Lấy danh sách nút kiến thức thành công");
    }

    @GetMapping("/nodes/{nodeId}")
    public ApiResponse<KnowledgeNodeDto> get(@PathVariable final Long nodeId) {
        return ApiResponse.success(
                nodeService.findById(nodeId), "Lấy chi tiết nút kiến thức thành công");
    }

    /**
     * The whole graph for one student, statuses included.
     *
     * <p>Deliberately one endpoint: fetching statuses per node would make the
     * client issue O(nodes) requests before it could lay out the graph.
     */
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> graph(final Principal principal) {
        return ApiResponse.success(
                Map.of(
                        "nodes", nodeService.findAll(),
                        "statuses", nodeService.resolveStatuses(currentUserId(principal))),
                "Lấy đồ thị kiến thức thành công");
    }

    @GetMapping("/statuses")
    public ApiResponse<List<StudentNodeStatusDto>> statuses(final Principal principal) {
        return ApiResponse.success(
                nodeService.resolveStatuses(currentUserId(principal)),
                "Lấy trạng thái nút kiến thức thành công");
    }

    /** Adjacency map, used by the client to draw edges. */
    @GetMapping("/prerequisites")
    public ApiResponse<Map<Long, List<Long>>> prerequisites() {
        return ApiResponse.success(
                nodeService.currentAdjacency(), "Lấy quan hệ tiên quyết thành công");
    }

    /* ----------------------------- Write ---------------------------- */

    @PostMapping("/nodes")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<KnowledgeNodeDto>> create(
            @Valid @RequestBody final CreateNodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                nodeService.create(request), "Tạo nút kiến thức thành công"));
    }

    @PutMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<KnowledgeNodeDto> update(
            @PathVariable final Long nodeId,
            @Valid @RequestBody final CreateNodeRequest request) {
        return ApiResponse.success(
                nodeService.update(nodeId, request), "Cập nhật nút kiến thức thành công");
    }

    /**
     * Links a prerequisite.
     *
     * <p>Rejects any edge that would close a cycle, answering 400 with a
     * Vietnamese message naming the offending chain.
     */
    @PostMapping("/prerequisites")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<KnowledgeNodeDto> addPrerequisite(
            @Valid @RequestBody final AddPrerequisiteRequest request) {
        return ApiResponse.success(
                nodeService.addPrerequisite(request), "Thêm quan hệ tiên quyết thành công");
    }

    @DeleteMapping("/prerequisites/{prerequisiteNodeId}/{dependentNodeId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> removePrerequisite(
            @PathVariable final Long prerequisiteNodeId,
            @PathVariable final Long dependentNodeId) {
        nodeService.removePrerequisite(prerequisiteNodeId, dependentNodeId);
        return ApiResponse.success("Xoá quan hệ tiên quyết thành công");
    }

    /**
     * Reads the user id from the JWT subject.
     *
     * <p>The subject is the numeric id by construction in the token provider;
     * anything else means the token came from somewhere unexpected.
     */
    private static Long currentUserId(final Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (final NumberFormatException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}