package com.ri.artificial.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.po.SysKnowledge;
import com.ri.artificial.domain.query.KnowledgePageQuery;
import com.ri.artificial.domain.vo.preview.PreviewResultVO;
import com.ri.artificial.domain.dto.SplitterForm;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author Ri
 * @date 2026-10-01 11:12
 */
public interface IKnowledgeService extends IService<SysKnowledge> {
    /**
     * 分页查询知识库文件
     */
    Page<SysKnowledge> pageKnowledge(KnowledgePageQuery query);

    /**
     * 上传知识库文件
     */
    Result<String> uploadFiles(List<MultipartFile> files, String splitterName, SplitterForm params);

    /**
     * 分片预览（dry-run）：只解析 + 分片，不写 OSS / DB / Milvus。
     * 供用户在上传前查看不同策略的切分效果。
     */
    PreviewResultVO previewChunks(MultipartFile file, String splitterName, SplitterForm params);

    /**
     * 删除知识库文件（同步删除 OSS 中的文件）
     */
    Result<String> deleteKnowledge(Long userId, List<Long> ids);

    /**
     * 查询对应的知识库列表ID
     */
    List<String> listVectorIds(List<Long> docIds);
}

