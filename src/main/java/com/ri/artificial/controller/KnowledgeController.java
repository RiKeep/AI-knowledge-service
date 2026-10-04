package com.ri.artificial.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.SplitterForm;
import com.ri.artificial.domain.po.SysKnowledge;
import com.ri.artificial.domain.query.KnowledgePageQuery;
import com.ri.artificial.domain.vo.PageVO;
import com.ri.artificial.domain.vo.preview.PreviewResultVO;
import com.ri.artificial.domain.vo.SplitterInfoVO;
import com.ri.artificial.service.IKnowledgeService;
import com.ri.artificial.splitter.SplitterRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author Ri
 * @date 2026-10-01 14:57
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final IKnowledgeService knowledgeService;
    private final SplitterRegistry splitterRegistry;

    @GetMapping("/list")
    public Result<PageVO<SysKnowledge>> pageKnowledge(@Valid KnowledgePageQuery query) {
        return Result.success(PageVO.of(knowledgeService.pageKnowledge(query)));
    }

    @PostMapping("/upload")
    public Result<String> uploadKnowledge(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam(defaultValue = "token") String splitterName,
                                          @Valid SplitterForm form) {
        if(CollUtil.isEmpty(files)){
            return Result.error("请选择要上传的文件");
        }
        return knowledgeService.uploadFiles(files, splitterName, form);
    }

    @DeleteMapping("/del/batch/{ids}")
    public Result<String> deleteKnowledgeByIds(@PathVariable List<Long> ids) {
        return knowledgeService.deleteKnowledge(StpUtil.getLoginIdAsLong(), ids);
    }

    /** 全部可用分片策略及其参数定义，前端据此动态渲染配置表单 */
    @GetMapping("/splitters")
    public Result<List<SplitterInfoVO>> splitters() {
        return Result.success(splitterRegistry.all().stream()
                .map(s -> new SplitterInfoVO(s.name(), s.description(), s.options()))
                .toList()
        );
    }

    /**
     * 分片预览（dry-run）：只解析 + 分片，不写 OSS / DB / Milvus。
     * 用户在上传前选择策略后调用，确认效果后再走 /upload 真实入库。
     */
    @PostMapping("/preview")
    public Result<PreviewResultVO> preview(@RequestParam("file") MultipartFile file,
                                           @RequestParam("splitterName") String splitterName,
                                           @Valid SplitterForm form) {
        return Result.success(knowledgeService.previewChunks(file, splitterName, form));
    }
}
